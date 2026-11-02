# NOWHERE Pro 구독 설계

- 작성일: 2026-10-18
- 상태: 검토 대기

## 1. 목표

기존 기능 일부를 구독자 전용(Pro)으로 전환해 수익화한다.

| 구분 | 기능 |
|---|---|
| **Pro 전용** | 보고서 / 저장 기록 관리 (`RecordManagementActivity`, `ReportActivity`), 시간순 기록 (`TimelineActivity`) |
| 무료 유지 | 실시간 기록·위치 검증, 현재 위치, 사진 촬영, 경로 지도, 사진 기록/위치/방향 지도, 통합 기록 지도 |

성공 기준:
- 무료 사용자가 Pro 기능을 누르면 결제 화면이 뜨고, 구독 후 즉시 해당 화면으로 이동한다.
- 구독자는 오프라인(산간 지역 등)에서도 최대 7일간 Pro 기능을 쓸 수 있다.
- 해지·만료 시 다음 실행에서 무료로 돌아가며, 기록 데이터는 어떤 경우에도 삭제되지 않는다.

## 2. 상품 구성 (Play Console)

- 구독 상품 ID: `nowhere_pro`
- 기본 요금제(base plan): `monthly` (1개월 자동 갱신), `yearly` (1년 자동 갱신, 할인)
- 무료 체험·프로모션 혜택: 이번 범위에서 제외 (추후 Play Console에서 오퍼 추가만으로 확장 가능하도록 오퍼 토큰은 동적으로 선택)
- 가격은 앱에 하드코딩하지 않고 `ProductDetails`의 현지화 가격을 표시한다.

## 3. 접근 방식

**Google Play Billing Library 직접 연동 + 기기 내 구매 확인** (서버 없음).

선정 이유: 서버·계정이 없는 현재 구조와 "제3자 서비스 미사용" 방침에 부합. RevenueCat 등 외부 SDK(제3자 데이터 전송·수수료)와 자체 서버 검증(운영 부담)은 기각.

알려진 한계: 서버 영수증 검증이 없어 루팅 기기 등에서의 우회를 완전히 막지 못한다. 판정 로직(`ProStatus`)을 결제 연동과 분리해, 추후 서버 검증을 추가할 때 판정부만 교체할 수 있게 한다.

## 4. 구성 요소

| 파일 | 상태 | 책임 |
|---|---|---|
| `BillingManager.kt` | 신규 | `BillingClient` 연결·재연결, 상품 조회, 구매 시작, 구매 승인(acknowledge), 구독 상태 갱신. `Application` 수명의 싱글턴. 상태 변화는 리스너로 알림 |
| `ProStatus.kt` | 신규 | 순수 Kotlin 판정 로직(`isPro(purchases, cached, now)`) + `SharedPreferences` 캐시(`isPro`, `lastVerifiedAt`). Android 의존 없는 판정 함수는 단위 테스트 대상 |
| `PaywallActivity.kt` | 신규 | Pro 혜택 소개, 월간/연간 선택·구매, 구매 복원, Play 구독 관리 페이지 링크. 인텐트로 "구매 후 열 화면"을 전달받음 |
| `NowhereApp.kt` | 신규 | `Application` 서브클래스. 앱 시작 시 `BillingManager` 초기화·구독 조회 |
| `MainActivity.kt` | 수정 | `WebAppInterface`의 `openTimeline`/`openReport`/`openRecordManagement`에 Pro 확인 추가, 미구독 시 `PaywallActivity`로 이동. `@JavascriptInterface isPro()` 추가 |
| `TimelineActivity.kt`, `RecordManagementActivity.kt`, `ReportActivity.kt` | 수정 | `onCreate`에서 Pro 2차 확인 — 미구독이면 결제 화면으로 보내고 `finish()` |
| `AndroidManifest.xml` | 수정 | `android:name=".NowhereApp"`, `PaywallActivity` 등록 |
| `app/build.gradle` | 수정 | Play Billing Library(구현 시점 최신 안정 버전, `billing-ktx`) 추가, `versionCode` 증가, 비활성화된 테스트 설정 제거 후 JUnit 의존성 추가 |
| 개인정보처리방침 (`nowhere-privacy` 저장소) | 수정 | "결제는 Google Play가 처리하며 당사는 결제 정보를 수집·저장하지 않음" 명시 |

## 5. 데이터 흐름

### 5.1 앱 시작
1. `NowhereApp.onCreate` → `BillingManager.startConnection()`.
2. 연결 성공 → `queryPurchasesAsync(SUBS)`.
3. `ProStatus` 판정:
   - `nowhere_pro` 포함 & `purchaseState == PURCHASED` → Pro. 미승인 구매는 즉시 `acknowledgePurchase` (미승인 3일 경과 시 Play 자동 환불).
   - Play가 정상 응답했는데 해당 구매 없음 → 무료 (해지 만료·계정 보류는 Play가 목록에서 제외).
   - Play 연결/조회 실패 → 캐시값 사용. 단 `lastVerifiedAt`로부터 **7일 초과 시 무료**로 판정.
4. 성공 판정 시 캐시(`isPro`, `lastVerifiedAt = now`) 저장.

### 5.2 Pro 기능 진입
- `WebAppInterface.openTimeline()` 등 → `ProStatus.isPro` true면 기존대로 화면 열기, false면 `PaywallActivity`(extra: 목표 화면).

### 5.3 구매
1. `PaywallActivity` → `queryProductDetailsAsync(nowhere_pro)` → 월간/연간 요금제와 현지화 가격 표시.
2. 선택 → 해당 base plan의 `offerToken`으로 `launchBillingFlow`.
3. `onPurchasesUpdated`:
   - `PURCHASED` → 승인 → 캐시 갱신 → 목표 화면 열고 `finish()`.
   - `PENDING` → "결제 확인 중입니다" 안내. 이후 앱 시작/복원 시 `PURCHASED`가 되면 Pro.
4. "구매 복원" → 5.1의 2~4단계 재실행 후 결과 안내.

## 6. 오류 처리

| 상황 | 처리 |
|---|---|
| `USER_CANCELED` | 안내 없이 결제 화면 유지 |
| `ITEM_ALREADY_OWNED` | 자동 복원 실행 |
| `SERVICE_DISCONNECTED` / `SERVICE_UNAVAILABLE` | 지수 백오프 재연결(1s→2s→4s… 최대 30s), 결제 화면에 "Google Play에 연결할 수 없습니다" |
| `BILLING_UNAVAILABLE` (Play 미설치·미지원 기기) | 구매 버튼 비활성화 + 안내 |
| 상품 조회 실패/빈 결과 | 구매 버튼 비활성화, "잠시 후 다시 시도해 주세요" |
| `ERROR` / `NETWORK_ERROR` 등 기타 | 토스트로 일반 오류 안내, 결제 화면 유지 |

## 7. 테스트

- **단위 테스트** (`app/src/test`): `ProStatus` 판정 함수 — 구매 있음/없음, PENDING, 미승인, Play 실패 시 캐시 사용, 7일 경계(정확히 7일/초과).
  - 현재 `app/build.gradle`의 `tasks.withType(Test) { enabled = false }` 및 `unitTests.all { enabled = false }` 를 제거해 테스트를 다시 활성화한다.
- **수동 테스트** (개발자 진행): Play Console 내부 테스트 트랙 + 라이선스 테스터 계정
  1. 무료 상태에서 타임라인/보고서/기록 관리 → 결제 화면 표시
  2. 월간 구매 → 즉시 목표 화면 진입, 재실행 후에도 Pro 유지
  3. 비행기 모드로 재실행 → Pro 유지
  4. Play에서 구독 해지 → (테스트 계정은 수 분 단위 갱신) 만료 후 재실행 시 무료
  5. 앱 삭제·재설치 후 "구매 복원" → Pro 복귀
  6. 연간 요금제로 위 2번 반복
  7. 결제 수단 거절(테스트 카드 "항상 거절") → 오류 안내, 무료 유지

## 8. 개발자 작업 (Play Console)

1. 결제 프로필(판매자 계정) 설정
2. 수익 창출 → 구독 → `nowhere_pro` 생성, base plan `monthly` / `yearly` 생성·가격 설정·활성화
3. 설정 → 라이선스 테스트에 테스터 Gmail 등록
4. Billing 라이브러리가 포함된 빌드를 내부 테스트 트랙에 업로드 (업로드 전에는 구독 상품 생성 메뉴가 열리지 않을 수 있음)
5. 스토어 등록정보·데이터 보안 양식에 "인앱 구매" 반영

## 9. 범위 외

무료 체험/프로모션, 서버 영수증 검증, 실시간 개발자 알림(RTDN), 광고, 기존 기능 외 신규 Pro 기능, 웹(WebView) UI의 잠금 아이콘 표시(브리지 `isPro()`만 제공, UI 반영은 후속).
