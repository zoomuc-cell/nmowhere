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

화면 HTML을 고칠 때는 안드로이드의 `*Activity.kt`와 iOS의 `MainHTML.swift` / `ScreenHTML.swift`를 함께 고쳐 주세요.

## 빌드

Mac에서 Xcode 15 이상으로 `ios/NOWHERE.xcodeproj`를 열고 실행합니다.
실제 아이폰에 설치하려면 Xcode의 Signing & Capabilities에서 본인 Apple 계정(Team)을 선택해야 합니다.
TestFlight나 App Store 배포에는 Apple Developer Program 가입이 필요합니다.

GitHub에 올리면 `.github/workflows/ios.yml`이 Mac에서 시뮬레이터용으로 자동 빌드해 컴파일 오류를 확인합니다.
