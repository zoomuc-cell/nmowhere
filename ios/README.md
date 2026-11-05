# NOWHERE iOS

안드로이드 앱(`app/`)과 똑같이 동작하는 아이폰용 앱입니다.

## 구조

안드로이드 앱의 화면은 모두 WebView 안의 HTML이라서, iOS도 같은 HTML을 WKWebView로 그대로 보여줍니다.
HTML이 부르는 `Android.함수이름()`은 `WebScreenViewController`가 받아 iOS 코드로 연결합니다.

| 안드로이드 | iOS |
| --- | --- |
| `MainActivity` (홈, 위치 추적, 사진 촬영) | `Screens/MainViewController.swift` |
| `MapActivity`, `PhotoMapActivity`, `PhotoDirectionMapActivity`, `IntegratedMapActivity`, `TimelineActivity`, `CurrentLocationActivity` | `Screens/Screens.swift` + `Screens/ScreenHTML.swift` |
| `ReportActivity` (보고서, PDF) | `Screens/ReportViewController.swift` |
| `PhotoRecordsActivity` | `Screens/PhotoRecordsViewController.swift` |
| `RecordManagementActivity` | `Screens/RecordManagementViewController.swift` |
| `MovementDetector` (자이로, 가속도, 기압) | `Core/MovementDetector.swift` (CoreMotion) |
| `DirectionSensor` (방위각) | `Core/DirectionSensor.swift` (CoreLocation 나침반) |
| `MovementDatabase`, `PhotoDatabase` (SharedPreferences) | `Core/Records.swift` (앱 전용 폴더의 JSON 파일) |
| Fused Location | CoreLocation |
| 인쇄로 PDF 저장 | A4 PDF 생성 후 공유 창 (파일에 저장, 인쇄, 보내기) |
| `billing/` (Google Play 구독, `PaywallActivity`, `ProGate`) | `Billing/ProStore.swift`, `Billing/PaywallViewController.swift` (StoreKit 2) |

화면 HTML을 고칠 때는 안드로이드의 `*Activity.kt`와 iOS의 `MainHTML.swift` / `ScreenHTML.swift`를 함께 고쳐 주세요.

## 빌드

Mac에서 Xcode 15 이상으로 `ios/NOWHERE.xcodeproj`를 열고 실행합니다.
실제 아이폰에 설치하려면 Xcode의 Signing & Capabilities에서 본인 Apple 계정(Team)을 선택해야 합니다.
TestFlight나 App Store 배포에는 Apple Developer Program 가입이 필요합니다.

GitHub에 올리면 `.github/workflows/ios.yml`이 Mac에서 시뮬레이터용으로 자동 빌드해 컴파일 오류를 확인합니다.

## Pro 구독 (App Store)

안드로이드와 같이 시간순 기록, 보고서, 저장 기록 관리는 Pro 전용입니다. 나머지 기능은 무료입니다.
결제는 StoreKit 2로 기기에서 바로 확인하며 서버는 쓰지 않습니다. 구독 확인이 안 되는 상황(오프라인 등)에서는 마지막 확인 뒤 7일까지 Pro를 유지합니다.

App Store Connect에서 할 일:

1. 앱 > 수익화 > 구독 에서 구독 그룹 `NOWHERE Pro` 생성
2. 그 그룹에 자동 갱신 구독 2개 생성: `nowhere_pro_monthly` (1개월), `nowhere_pro_yearly` (1년)
3. 각 상품에 가격, 한국어 표시 이름·설명, 심사용 스크린샷(결제 화면) 입력
4. 앱 정보 > 개인정보처리방침 URL: `https://zoomuc-cell.github.io/nowhere-privacy/`
5. 첫 버전 심사 제출 때 두 구독 상품도 함께 선택해 제출

결제 화면에는 심사 기준(3.1.2)에 맞게 가격·기간, 자동 갱신 안내, 구매 복원, 개인정보처리방침과 이용약관(Apple 표준 EULA) 링크가 있습니다.

### 심사 메모 예시 (App Review Information > Notes)

```
NOWHERE records the user's movement path and geotagged photos only on the device.
No account or login is required. Location is used only while the app is open.
Timeline, Report and Record Management are available with the NOWHERE Pro
auto-renewable subscription (monthly / yearly). Purchases can be tested with a
Sandbox account; "구매 복원" restores purchases.
```

## TestFlight 업로드

GitHub > Actions > iOS build > Run workflow 에서 **testflight**를 체크하고 실행하면 Release로 빌드해 App Store Connect에 올립니다.
빌드 번호는 실행할 때마다 자동으로 올라갑니다 (100 + 실행 번호).

처음 한 번 저장소 Settings > Secrets and variables > Actions 에 아래 4개를 넣어야 합니다.

| 이름 | 값 |
| --- | --- |
| `APPLE_TEAM_ID` | Apple Developer 계정의 Team ID (10자리) |
| `ASC_KEY_ID` | App Store Connect > 사용자 및 액세스 > 통합 > API 키의 키 ID (권한: 앱 관리) |
| `ASC_ISSUER_ID` | 같은 화면 위쪽의 Issuer ID |
| `ASC_KEY_P8` | 내려받은 `AuthKey_XXXX.p8` 파일 내용 전체 |

App Store Connect에 번들 ID `com.auratechnologies.nowhere`로 앱이 먼저 만들어져 있어야 업로드됩니다.
