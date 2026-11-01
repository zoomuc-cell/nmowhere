import UIKit
import WebKit

/// Android의 "WebView 하나를 화면에 꽉 채우는 Activity" 대응.
/// HTML 안에서 부르는 `Android.함수이름()` 호출을 iOS 네이티브 코드로 전달한다.
class WebScreenViewController: UIViewController, WKNavigationDelegate, WKUIDelegate {

    let webView: WKWebView
    private let html: String
    private let baseURL: URL?

    /// 페이지가 `Android.xxx(...)`를 부르면 (함수 이름, 인자)로 호출된다.
    var onBridgeCall: ((String, [Any]) -> Void)?

    /// 페이지 로드가 끝나면 한 번 호출된다.
    var onPageLoaded: (() -> Void)?

    init(title: String?, html: String, baseURL: URL? = URL(string: "https://localhost/")) {
        self.html = html
        self.baseURL = baseURL

        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true

        // window.Android 객체를 만들어 어떤 함수 이름이 불려도 iOS로 전달한다.
        let bridgeScript = """
        window.Android = new Proxy({}, {
            get: function(target, name) {
                return function() {
                    window.webkit.messageHandlers.Android.postMessage({
                        method: String(name),
                        args: Array.prototype.slice.call(arguments)
                    });
                };
            }
        });
        """
        configuration.userContentController.addUserScript(
            WKUserScript(source: bridgeScript, injectionTime: .atDocumentStart, forMainFrameOnly: true)
        )

        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init(nibName: nil, bundle: nil)
        self.title = title

        configuration.userContentController.add(WeakScriptHandler(self), name: "Android")
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        webView.configuration.userContentController.removeScriptMessageHandler(forName: "Android")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webView)

        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])

        webView.loadHTMLString(html, baseURL: baseURL)
    }

    /// Android webView.evaluateJavascript(...) 대응
    func evaluate(_ script: String) {
        webView.evaluateJavaScript(script, completionHandler: nil)
    }

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        let callback = onPageLoaded
        onPageLoaded = nil
        callback?()
    }

    // MARK: - WKUIDelegate (페이지의 alert() 표시)

    func webView(_ webView: WKWebView,
                 runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo,
                 completionHandler: @escaping () -> Void) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "확인", style: .default) { _ in completionHandler() })
        present(alert, animated: true)
    }

    fileprivate func handleBridgeMessage(_ body: Any) {
        guard let dict = body as? [String: Any],
              let method = dict["method"] as? String else { return }
        let args = dict["args"] as? [Any] ?? []
        onBridgeCall?(method, args)
    }
}

/// WKUserContentController가 뷰 컨트롤러를 강하게 잡아 메모리가 새지 않도록 한다.
private final class WeakScriptHandler: NSObject, WKScriptMessageHandler {
    weak var target: WebScreenViewController?

    init(_ target: WebScreenViewController) {
        self.target = target
    }

    func userContentController(_ userContentController: WKUserContentController,
                               didReceive message: WKScriptMessage) {
        target?.handleBridgeMessage(message.body)
    }
}
