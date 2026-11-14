# NOWHERE

**LOCATION · PHOTO · TRACK · EVIDENCE**

NOWHERE는 내가 어디로 어떻게 이동했는지, 어디서 어느 방향으로 사진을 찍었는지를 휴대폰에 기록해 두는 앱입니다.
기록은 모두 기기 안에만 저장되며, 회원가입이나 로그인이 없습니다.

- 만든 곳: Aura Technologies (aura-technologies.co.kr)
- 문의: admin@aura-technologies.co.kr
- 개인정보처리방침: https://zoomuc-cell.github.io/nowhere-privacy/

## 주요 기능

| 구분 | 기능 | 무료 / Pro |
| --- | --- | --- |
| 지금 이 순간 | 현장 사진 촬영 (촬영 순간의 위치·방향·이동 상태를 함께 저장, 사진 EXIF에도 기록) | 무료 |
| | 현재 위치 (좌표, 고도, 정확도, 방향) | 무료 |
| 사진 기록 | 촬영 사진 기록, 사진 촬영 위치 지도, 사진 촬영 방향 지도 | 무료 |
| | 이동 경로 지도 (도보·차량·엘리베이터 등 이동 상태 표시) | 무료 |
| 통합 기록 | 통합 기록 지도 | 무료 |
| | 시간순 기록 (타임라인) | Pro |
| | 기록 보고서 (PDF 저장·공유) | Pro |
| | 저장 기록 관리 (날짜별 보기·삭제) | Pro |

Pro는 월간/연간 자동 갱신 구독입니다. 안드로이드는 Google Play 결제, 아이폰은 App Store 결제를 씁니다.
구독이 끝나도 기록은 지워지지 않습니다.

## 폴더 구조

```
app/                  안드로이드 앱 (Kotlin)
  src/main/java/...   화면(Activity)과 센서, 저장, 결제(billing/)
ios/                  아이폰 앱 (Swift)
  NOWHERE/Core/       위치·동작 센서, 기록 저장, 화면 공통 부분
  NOWHERE/Screens/    화면들 (안드로이드와 같은 HTML을 WebView로 표시)
  NOWHERE/Billing/    Pro 구독 (StoreKit 2)
  README.md           아이폰 앱 설명, App Store 출시 준비
.github/workflows/    자동 빌드 (android.yml, ios.yml)
codemagic.yaml        Codemagic에서 아이폰 앱을 TestFlight에 올리는 설정
docs/                 설계 문서 (Pro 구독)
```

두 앱의 화면은 같은 HTML로 되어 있습니다. 화면을 고칠 때는 안드로이드의 `*Activity.kt`와 아이폰의 `ios/NOWHERE/Screens/*HTML.swift`를 함께 고쳐 주세요.

| 항목 | 안드로이드 | 아이폰 |
| --- | --- | --- |
| 앱 ID | `com.auratechnologies.nowhere` | `com.auratechnologies.nowhere` |
| 버전 | 5.4 (10) | 5.3 (9) |
| 최소 OS | Android 7.0 (API 24) | iOS 15 |
| 위치·센서 | Fused Location, 자이로·가속도·기압계 | CoreLocation, CoreMotion |
| 기록 저장 | 기기 안 (SharedPreferences) | 기기 안 (앱 전용 폴더의 파일) |
| 지도 | Leaflet(앱에 포함, `app/src/main/assets/leaflet`) + OpenStreetMap 지도 이미지 (인터넷 필요) | 같음 |

## 빌드와 배포

### 안드로이드

- `app/`, gradle 파일을 바꿔 GitHub에 올리면 **Android build**가 자동으로 실행됩니다.
- main 브랜치에서 빌드되면 [테스트용 최신 버전 릴리스](https://github.com/zoomuc-cell/nowhere/releases/tag/test-latest)에 `nowhere-test.apk`가 올라갑니다. 휴대폰에서 눌러 받아 바로 설치할 수 있고, 스토어 앱과 따로 'NOWHERE 테스트'로 설치됩니다.
- 직접 빌드: Android Studio로 저장소 폴더를 열거나 `./gradlew assembleDebug` (JDK 17)

### 아이폰

- `ios/`를 바꿔 GitHub에 올리면 **iOS build**가 Mac에서 시뮬레이터용으로 자동 빌드해 오류를 확인합니다.
- TestFlight 업로드는 두 가지 중 하나를 쓰면 됩니다. 빌드 번호가 겹치지 않게 한 가지만 쓰는 것을 권합니다.
  - **Codemagic**: 앱 화면에서 Start new build > 브랜치 `main`, 워크플로 `NOWHERE iOS TestFlight`
  - **GitHub Actions**: Actions > iOS build > Run workflow 에서 `testflight` 체크
- 처음 설정(App Store Connect API 키, 인증서, 구독 상품 만들기)은 [ios/README.md](ios/README.md)에 단계별로 적어 두었습니다.
- 직접 빌드: Mac의 Xcode 15 이상에서 `ios/NOWHERE.xcodeproj`를 열고 Signing & Capabilities에서 팀을 고른 뒤 실행

## 출시 전 확인할 것

- [ ] App Store Connect에 앱(`com.auratechnologies.nowhere`)과 구독 상품 `nowhere_pro_monthly`, `nowhere_pro_yearly` 만들기
- [ ] Play Console에 구독 상품 `nowhere_pro` (요금제 `monthly`, `yearly`) 만들기
- [ ] 개인정보처리방침에 결제 처리(Apple/Google)와 문의 메일 반영 확인
- [ ] TestFlight / 내부 테스트 트랙에서 결제·구매 복원 시험
- [ ] 실제 폰에서 사진 촬영(가로·세로), 이동 경로 기록, 보고서 PDF 저장 시험
- [ ] 스토어 등록정보: 스크린샷, 설명, 심사 메모 ([ios/README.md](ios/README.md)의 예시 참고)
