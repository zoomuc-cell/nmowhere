import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Android NowhereApp.onCreate 대응: 앱 시작 시 구독 상태 확인
        ProStore.shared.start()
        return true
    }

    func application(_ application: UIApplication,
                     configurationForConnecting connectingSceneSession: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Default", sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene,
               willConnectTo session: UISceneSession,
               options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }

        let navigation = LightStatusNavigationController(rootViewController: MainViewController())
        navigation.navigationBar.prefersLargeTitles = false

        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = navigation
        window.makeKeyAndVisible()
        self.window = window
    }
}

/// 홈 화면(어두운 배경)에서는 상태 표시줄 글자를 흰색으로 보이게 한다.
final class LightStatusNavigationController: UINavigationController {
    override var childForStatusBarStyle: UIViewController? {
        return topViewController
    }
}
