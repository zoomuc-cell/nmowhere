# NOWHERE Pro 구독 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 타임라인·기록 관리·보고서 화면을 Google Play 구독(`nowhere_pro`, 월간/연간) 구독자 전용으로 잠근다.

**Architecture:** 순수 Kotlin 판정 로직(`ProStatus`)과 Android 결제 연동(`BillingManager`)을 분리한다. `BillingManager`는 `Application` 수명 싱글턴으로 앱 시작 시 구독을 조회해 `ProStore`(SharedPreferences) 캐시를 갱신하고, 모든 잠금 확인은 `ProGate` 한 곳을 거친다. 서버는 없다.

**Tech Stack:** Kotlin, AppCompat, Google Play Billing Library (`com.android.billingclient:billing-ktx`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-18-pro-subscription-design.md`

## Global Constraints

- 구독 상품 ID `nowhere_pro`, base plan ID `monthly`, `yearly` — 상수는 `BillingConstants.kt` 한 곳에만 정의.
- 오프라인 유예: 마지막 성공 확인 후 **7일(604_800_000 ms) 이하면 캐시 인정, 초과면 무료**.
- 가격 문자열 하드코딩 금지 — `ProductDetails.SubscriptionOfferDetails.pricingPhases`의 `formattedPrice`만 표시.
- Billing 라이브러리: Google Maven의 최신 안정 버전 사용, **최소 8.0.0** (`enablePendingPurchases(PendingPurchasesParams…)`, `enableAutoServiceReconnection()` API 기준). 버전은 `gradle/libs.versions.toml`에 추가하지 말고 기존 관례대로 `app/build.gradle`에 직접 문자열로 적는다.
- 기존 `minSdk 24`, `compileSdk 35`, Java/JVM 1.8 설정 유지.
- UI는 기존 Activity들처럼 XML 레이아웃 없이 코드(`LinearLayout`)로 구성. 사용자 문구는 한국어.
- 기록 데이터(`MovementDatabase`, `PhotoDatabase`)는 읽기만, 절대 삭제·수정하지 않는다.
- 이 세션 환경에는 Android SDK가 없고 Google Maven 접근이 차단되어 있다. Gradle 검증 명령은 **개발자 PC(Android Studio)** 에서 실행한다. 그래서 이 세션에서는 코드 작성·정적 검토까지만 가능하고, 빌드·테스트 통과 여부는 개발자 PC에서 확인해야 한다.

## Review Focus

1. **앱 시작 직후(결제 연결 전) 잠긴 기능을 누름** → 캐시값으로 판정해야 하며, 연결 대기로 멈추거나 구독자를 결제 화면으로 보내면 안 된다. (Task 1 `cacheUsedWhenQueryNotYetDone` 테스트)
2. **기기 시계가 과거로 바뀌어 `lastVerifiedAt`가 미래** → 경과 시간이 음수이므로 유예 안으로 본다(구독자를 잠그지 않음). 온라인이 되면 Play 응답이 덮어쓴다. (Task 1 `futureLastVerifiedTreatedAsWithinGrace`)
3. **결제 진행 중 PaywallActivity가 종료됨(프로세스 종료·회전)** → 구매 결과는 `BillingManager`의 앱 수명 리스너가 받아 승인·캐시 갱신해야 한다. 다음 실행 시 Pro. (Task 2 수동 확인 항목 + 승인 로직이 Activity가 아닌 `BillingManager`에 있음)
4. **잠긴 버튼을 빠르게 두 번 탭** → 결제 화면이 두 개 쌓이면 안 된다. (Task 4 `FLAG_ACTIVITY_SINGLE_TOP` + Task 3 Paywall `launchMode="singleTop"`)
5. **보고서처럼 extras가 필요한 화면으로 가다 결제한 경우** → 구매 후 원래 intent(extras 포함)로 열려야 한다. (Task 4: 원본 `Intent`를 그대로 전달)

---

## File Structure

| 파일 | 책임 |
|---|---|
| `app/src/main/java/com/auratechnologies/nowhere/billing/BillingConstants.kt` | 상품/요금제 ID, 유예 기간 상수 |
| `.../billing/ProStatus.kt` | 순수 판정 로직 (Android import 금지) |
| `.../billing/ProStore.kt` | SharedPreferences 캐시 읽기/쓰기 |
| `.../billing/BillingManager.kt` | BillingClient 연결·조회·구매·승인, 상태 리스너 |
| `.../NowhereApp.kt` | Application, BillingManager 초기화 |
| `.../billing/ProGate.kt` | "Pro면 열고 아니면 결제 화면" 단일 진입점 |
| `.../billing/PaywallActivity.kt` | 결제 화면 |
| `app/src/test/java/com/auratechnologies/nowhere/billing/ProStatusTest.kt` | 판정 로직 단위 테스트 |
| 수정: `MainActivity.kt`, `TimelineActivity.kt`, `RecordManagementActivity.kt`, `ReportActivity.kt`, `AndroidManifest.xml`, `app/build.gradle` | |

---

### Task 1: 테스트 재활성화 + 순수 판정 로직 `ProStatus`

**Files:**
- Modify: `app/build.gradle` — `tasks.withType(Test) { enabled = false }` 블록과 `android.testOptions { unitTests.all { enabled = false } }` 블록 삭제; `dependencies`에 `testImplementation 'junit:junit:4.13.2'` 추가
- Create: `.../billing/BillingConstants.kt`, `.../billing/ProStatus.kt`
- Test: `app/src/test/java/com/auratechnologies/nowhere/billing/ProStatusTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object BillingConstants {
      const val PRODUCT_ID = "nowhere_pro"
      const val PLAN_MONTHLY = "monthly"
      const val PLAN_YEARLY = "yearly"
      const val OFFLINE_GRACE_MS = 7L * 24 * 60 * 60 * 1000
  }
  enum class PurchaseState { PURCHASED, PENDING, OTHER }
  data class PurchaseSnapshot(val productIds: List<String>, val state: PurchaseState,
                              val acknowledged: Boolean, val purchaseToken: String)
  sealed class QueryOutcome {
      data class Success(val purchases: List<PurchaseSnapshot>) : QueryOutcome()
      object Failure : QueryOutcome()      // 연결/조회 실패
      object NotYetQueried : QueryOutcome() // 앱 시작 직후
  }
  data class ProCache(val isPro: Boolean, val lastVerifiedAtMs: Long)
  data class ProDecision(val isPro: Boolean, val cacheToSave: ProCache?, val tokensToAcknowledge: List<String>)
  object ProStatus { fun decide(outcome: QueryOutcome, cache: ProCache, nowMs: Long): ProDecision }
  ```

- [ ] **Step 1: Write the failing tests** (`ProStatusTest`, 공통 `now = 1_000_000_000_000L`, `DAY = 86_400_000L`)

```kotlin
@Test fun purchasedSubscriptionIsPro() {
    val p = PurchaseSnapshot(listOf("nowhere_pro"), PurchaseState.PURCHASED, true, "t1")
    val d = ProStatus.decide(QueryOutcome.Success(listOf(p)), ProCache(false, 0), now)
    assertTrue(d.isPro); assertEquals(ProCache(true, now), d.cacheToSave); assertEquals(emptyList<String>(), d.tokensToAcknowledge)
}
@Test fun unacknowledgedPurchaseIsProAndNeedsAck() {
    val p = PurchaseSnapshot(listOf("nowhere_pro"), PurchaseState.PURCHASED, false, "t1")
    val d = ProStatus.decide(QueryOutcome.Success(listOf(p)), ProCache(false, 0), now)
    assertTrue(d.isPro); assertEquals(listOf("t1"), d.tokensToAcknowledge)
}
@Test fun pendingPurchaseIsNotPro() {
    val p = PurchaseSnapshot(listOf("nowhere_pro"), PurchaseState.PENDING, false, "t1")
    val d = ProStatus.decide(QueryOutcome.Success(listOf(p)), ProCache(false, 0), now)
    assertFalse(d.isPro); assertEquals(ProCache(false, now), d.cacheToSave); assertEquals(emptyList<String>(), d.tokensToAcknowledge)
}
@Test fun otherProductIsIgnored() { /* productIds = ["something_else"], PURCHASED → isPro false */ }
@Test fun successWithNoPurchasesRevokesCachedPro() {
    val d = ProStatus.decide(QueryOutcome.Success(emptyList()), ProCache(true, now - DAY), now)
    assertFalse(d.isPro); assertEquals(ProCache(false, now), d.cacheToSave)
}
@Test fun failureUsesCacheWithinGrace() {
    val d = ProStatus.decide(QueryOutcome.Failure, ProCache(true, now - 7 * DAY), now)  // 정확히 7일
    assertTrue(d.isPro); assertNull(d.cacheToSave)
}
@Test fun failureRevokesAfterGrace() {
    val d = ProStatus.decide(QueryOutcome.Failure, ProCache(true, now - 7 * DAY - 1), now)
    assertFalse(d.isPro); assertNull(d.cacheToSave)
}
@Test fun cacheUsedWhenQueryNotYetDone() {
    val d = ProStatus.decide(QueryOutcome.NotYetQueried, ProCache(true, now - DAY), now)
    assertTrue(d.isPro); assertNull(d.cacheToSave)
}
@Test fun futureLastVerifiedTreatedAsWithinGrace() {
    val d = ProStatus.decide(QueryOutcome.Failure, ProCache(true, now + 30 * DAY), now)
    assertTrue(d.isPro)
}
@Test fun cachedFreeStaysFreeOnFailure() {
    assertFalse(ProStatus.decide(QueryOutcome.Failure, ProCache(false, now), now).isPro)
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :app:testDebugUnitTest --tests "*ProStatusTest"` → 컴파일 실패(`ProStatus` 없음)
- [ ] **Step 3: Implement** `BillingConstants`, 위 타입들, `ProStatus.decide` — Success면 `PRODUCT_ID`를 포함한 PURCHASED 구매 존재 여부로 판정하고 캐시 `(isPro, nowMs)`, 미승인 PURCHASED 토큰 수집; Failure/NotYetQueried면 `cache.isPro && (nowMs - cache.lastVerifiedAtMs) <= OFFLINE_GRACE_MS`, `cacheToSave = null`. `ProStatus.kt`에 `android.*` import 금지.
- [ ] **Step 4: Run tests** — 같은 명령 → 10 tests PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(billing): add ProStatus decision logic and re-enable unit tests"`

---

### Task 2: `ProStore` + `BillingManager` + `NowhereApp`

**Files:**
- Create: `.../billing/ProStore.kt`, `.../billing/BillingManager.kt`, `.../NowhereApp.kt`
- Modify: `app/build.gradle` (`implementation 'com.android.billingclient:billing-ktx:<최신 안정, ≥8.0.0>'`), `AndroidManifest.xml` (`<application android:name=".NowhereApp" …>`)

**Interfaces:**
- Consumes: Task 1 전체
- Produces:
  ```kotlin
  class ProStore(context: Context) {              // prefs 이름 "nowhere_pro", 키 "is_pro", "last_verified_at"
      fun load(): ProCache
      fun save(cache: ProCache)
  }
  class BillingManager private constructor(app: Application) {
      companion object { fun init(app: Application): BillingManager; fun get(): BillingManager }
      val isPro: Boolean                                   // 현재 판정 (항상 즉시 반환, 블로킹 없음)
      fun refresh(onDone: ((Boolean) -> Unit)? = null)     // queryPurchasesAsync → ProStatus.decide → 캐시 저장·승인
      fun queryPlans(onResult: (BillingResult, ProductDetails?) -> Unit)
      fun launchPurchase(activity: Activity, details: ProductDetails, basePlanId: String): BillingResult
      fun addListener(l: Listener); fun removeListener(l: Listener)
      interface Listener {
          fun onProChanged(isPro: Boolean)
          fun onPurchasePending()
          fun onPurchaseError(result: BillingResult)  // USER_CANCELED는 호출하지 않음
      }
  }
  ```

- [ ] **Step 1: `ProStore` 구현** — 미저장 시 `ProCache(false, 0L)`.
- [ ] **Step 2: `BillingManager` 구현**
  - `BillingClient.newBuilder(app).setListener(purchasesUpdatedListener).enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).enableAutoServiceReconnection().build()`
  - 초기 `isPro = ProStatus.decide(NotYetQueried, store.load(), now).isPro`
  - 연결 실패(`onBillingSetupFinished` 비 OK / `onBillingServiceDisconnected`) 시 1s→2s→4s… 최대 30s 백오프로 `startConnection` 재시도(`Handler(Looper.getMainLooper())`), 실패 동안 `refresh`는 `QueryOutcome.Failure`로 판정
  - `Purchase` → `PurchaseSnapshot` 변환: `purchaseState` PURCHASED/PENDING/그 외 OTHER, `products`, `isAcknowledged`, `purchaseToken`
  - `tokensToAcknowledge` 각각 `acknowledgePurchase`
  - `purchasesUpdatedListener`: OK → 받은 구매로 `decide(Success(...))` (PENDING만 있으면 `onPurchasePending`); `ITEM_ALREADY_OWNED` → `refresh()`; `USER_CANCELED` → 무시; 그 외 → `onPurchaseError`
  - `isPro` 값이 바뀔 때만 `onProChanged`, 리스너 호출은 메인 스레드
  - `launchPurchase`: `details.subscriptionOfferDetails`에서 `basePlanId`가 일치하는 항목 중 `offerId == null`(기본 요금제)의 `offerToken` 사용
- [ ] **Step 3: `NowhereApp.onCreate`** → `BillingManager.init(this).refresh()`; Manifest에 `android:name` 추가
- [ ] **Step 4: Verify** — `./gradlew :app:assembleDebug` BUILD SUCCESSFUL, `./gradlew :app:testDebugUnitTest` PASS. 기기 실행 시 logcat에 크래시 없음(Play 미등록 상태에서는 상품 조회가 비어 있는 것이 정상)
- [ ] **Step 5: Commit** — `git commit -m "feat(billing): add BillingManager, ProStore and Application init"`

---

### Task 3: `PaywallActivity`

**Files:**
- Create: `.../billing/PaywallActivity.kt`
- Modify: `AndroidManifest.xml` — `<activity android:name=".billing.PaywallActivity" android:label="NOWHERE Pro" android:launchMode="singleTop" android:exported="false" />`

**Interfaces:**
- Consumes: `BillingManager.queryPlans`, `launchPurchase`, `refresh`, `Listener`; `BillingConstants.PLAN_MONTHLY/PLAN_YEARLY`
- Produces: `PaywallActivity.EXTRA_TARGET_INTENT = "target_intent"`

화면 구성(코드 `LinearLayout`, 위→아래): 제목 "NOWHERE Pro" / 혜택 목록 "• 시간순 기록(타임라인)", "• 저장 기록 관리", "• 기록 보고서 생성·내보내기", "• 그동안 쌓인 모든 기록을 바로 확인" / 상태 텍스트 / 버튼 "월간 구독 · {가격}/월", "연간 구독 · {가격}/년" / "구매 복원" / "구독 관리" / 하단 안내 "구독은 Google Play 계정으로 결제되며, 기간 종료 24시간 전까지 해지하지 않으면 자동 갱신됩니다."

- [ ] **Step 1: 구현**
  - `onCreate`: 구매 버튼 비활성화, 상태 "구독 정보를 불러오는 중…", `queryPlans` → 각 base plan의 마지막 `pricingPhase.formattedPrice`로 버튼 라벨 채우고 활성화. 결과가 비었거나 실패면 "구독 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."; `BILLING_UNAVAILABLE`이면 "이 기기에서는 Google Play 결제를 사용할 수 없습니다."; `SERVICE_DISCONNECTED`/`SERVICE_UNAVAILABLE`이면 "Google Play에 연결할 수 없습니다."
  - 버튼 → `launchPurchase(this, details, PLAN_…)`
  - `onStart`/`onStop`에서 리스너 등록/해제. `onProChanged(true)` → `EXTRA_TARGET_INTENT`가 있으면 `startActivity(target)` 후 `finish()`. `onPurchasePending` → 상태 "결제 확인 중입니다. 완료되면 자동으로 Pro가 활성화됩니다." `onPurchaseError` → 토스트 "결제 중 오류가 발생했습니다. 다시 시도해 주세요."
  - "구매 복원" → `refresh { pro -> if (!pro) 토스트 "복원할 구독이 없습니다." }` (Pro면 `onProChanged`가 이동 처리)
  - "구독 관리" → `https://play.google.com/store/account/subscriptions?sku=nowhere_pro&package=$packageName` 를 `ACTION_VIEW`
  - 이미 Pro 상태로 열리면(`onCreate`에서 `isPro`) 즉시 target으로 이동 후 `finish()`
- [ ] **Step 2: Verify** — `assembleDebug` 성공. (실제 결제 흐름 수동 테스트는 화면 잠금이 붙는 Task 4에서 수행)
- [ ] **Step 3: Commit** — `git commit -m "feat(billing): add Pro paywall screen"`

---

### Task 4: `ProGate` + 기존 화면 잠금

**Files:**
- Create: `.../billing/ProGate.kt`
- Modify: `MainActivity.kt` (`WebAppInterface`의 `openTimeline`, `openReport`, `openRecordManagement` ≈ `:689-718`, `isPro()` 추가), `TimelineActivity.kt:18`, `RecordManagementActivity.kt:16`, `ReportActivity.kt:23` (`onCreate` 첫 줄 `super.onCreate` 직후)

**Interfaces:**
- Consumes: `BillingManager.get().isPro`; Task 3의 `PaywallActivity.EXTRA_TARGET_INTENT`(`"target_intent"`, `Intent` Parcelable)
- Produces:
  ```kotlin
  object ProGate {
      /** Pro면 target을 시작하고 true. 아니면 PaywallActivity(target 전달, FLAG_ACTIVITY_SINGLE_TOP)를 열고 false. */
      fun open(activity: Activity, target: Intent): Boolean
      /** 잠긴 Activity의 onCreate용. 미구독이면 자기 intent를 target으로 결제 화면을 열고 finish() 후 true. */
      fun redirectIfLocked(activity: Activity): Boolean
  }
  ```

- [ ] **Step 1: `ProGate` 구현**
- [ ] **Step 2: `WebAppInterface` 수정** — 세 함수의 `startActivity(Intent(...))`를 `ProGate.open(this@MainActivity, Intent(...))`로 교체(기존 대상 클래스 그대로 유지). `@JavascriptInterface fun isPro(): Boolean = BillingManager.get().isPro` 추가.
- [ ] **Step 3: 세 Activity의 `onCreate`** — `super.onCreate(savedInstanceState)` 다음 줄에 `if (ProGate.redirectIfLocked(this)) return`
- [ ] **Step 4: Verify** — `assembleDebug` 성공. 기기(미구독): 타임라인/보고서/기록 관리 → 결제 화면, 빠른 두 번 탭 시 결제 화면 1개(뒤로가기 한 번에 메인 복귀). 경로 지도·사진 지도·통합 지도·현재 위치·사진 촬영은 기존대로 열림. 이어서 내부 테스트 트랙 + 라이선스 테스터로 스펙 7장 수동 테스트 1~7 수행(특히 2: 구매 직후 원래 화면 진입, Review Focus 3: 결제 창에서 앱 강제 종료 → 재실행 시 Pro, Review Focus 5: 기록 관리 → 보고서 경로).
- [ ] **Step 5: Commit** — `git commit -m "feat(billing): gate timeline and record/report screens behind Pro"`

---

### Task 5: 출시 준비

**Files:**
- Modify: `app/build.gradle` — `versionCode 8 → 9`, `versionName '5.2' → '5.3'`
- Modify (다른 저장소 `zoomuc-cell/nowhere-privacy`): 개인정보처리방침의 "수집하지 않는 정보 — 결제 정보" 항목을 "결제는 Google Play가 처리하며, 당사는 카드번호 등 결제 정보를 수집·저장하지 않습니다. 앱은 구독 활성 여부만 기기에 저장합니다."로 변경, 제3자 서비스 항목에 Google Play 결제 추가, 최종 업데이트 날짜 갱신(한/영 모두)

- [ ] **Step 1: 버전 변경 후 `assembleRelease`** 성공 확인
- [ ] **Step 2: 개인정보처리방침 수정** — 실제 파일 구조를 읽고 위 문구 반영, `nowhere-privacy`에 같은 브랜치명으로 커밋·푸시
- [ ] **Step 3: Commit (nowhere)** — `git commit -m "chore: bump version to 5.3 for Pro subscription"`
- [ ] **Step 4: 개발자 Play Console 작업** — 스펙 8장 체크리스트 1~5 (개발자 직접)
