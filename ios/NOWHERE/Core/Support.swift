import UIKit

/// Android Toast 대응: 화면 아래쪽에 잠깐 메시지를 띄운다.
enum Toast {
    enum Length {
        case short, long
        var seconds: TimeInterval { self == .short ? 2.0 : 3.5 }
    }

    static func show(_ message: String, _ length: Length = .short) {
        DispatchQueue.main.async {
            guard let window = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .flatMap({ $0.windows })
                .first(where: { $0.isKeyWindow }) else { return }

            let label = PaddedLabel()
            label.text = message
            label.numberOfLines = 0
            label.textAlignment = .center
            label.font = .systemFont(ofSize: 15)
            label.textColor = .white
            label.backgroundColor = UIColor(white: 0.15, alpha: 0.92)
            label.layer.cornerRadius = 18
            label.layer.masksToBounds = true
            label.alpha = 0
            label.translatesAutoresizingMaskIntoConstraints = false

            window.addSubview(label)
            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: window.centerXAnchor),
                label.bottomAnchor.constraint(equalTo: window.safeAreaLayoutGuide.bottomAnchor, constant: -48),
                label.widthAnchor.constraint(lessThanOrEqualTo: window.widthAnchor, constant: -48)
            ])

            UIView.animate(withDuration: 0.2) { label.alpha = 1 }
            UIView.animate(withDuration: 0.3, delay: length.seconds, options: []) {
                label.alpha = 0
            } completion: { _ in
                label.removeFromSuperview()
            }
        }
    }

    private final class PaddedLabel: UILabel {
        private let insets = UIEdgeInsets(top: 12, left: 20, bottom: 12, right: 20)

        override func drawText(in rect: CGRect) {
            super.drawText(in: rect.inset(by: insets))
        }

        override var intrinsicContentSize: CGSize {
            let size = super.intrinsicContentSize
            return CGSize(width: size.width + insets.left + insets.right,
                          height: size.height + insets.top + insets.bottom)
        }

        override func textRect(forBounds bounds: CGRect, limitedToNumberOfLines numberOfLines: Int) -> CGRect {
            let inner = super.textRect(forBounds: bounds.inset(by: insets), limitedToNumberOfLines: numberOfLines)
            return inner.inset(by: UIEdgeInsets(top: -insets.top, left: -insets.left,
                                                bottom: -insets.bottom, right: -insets.right))
        }
    }
}

enum Format {
    /// Android SimpleDateFormat(pattern, Locale.KOREA) 대응
    static func date(_ millis: Int64, _ pattern: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = pattern
        return formatter.string(from: Date(timeIntervalSince1970: TimeInterval(millis) / 1000))
    }

    /// String.format(Locale.US, "%.Nf", value) 대응
    static func fixed(_ value: Double, _ digits: Int) -> String {
        return String(format: "%.\(digits)f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    /// Kotlin Float/Double.toInt() 대응 (소수점 버림)
    static func int(_ value: Double) -> Int {
        guard value.isFinite else { return 0 }
        return Int(value)
    }

    /// Android JSONArray.toString() 대응: 페이지 스크립트에 그대로 넣을 JSON 문자열
    static func json(_ object: Any) -> String {
        guard JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object, options: []),
              let text = String(data: data, encoding: .utf8) else { return "[]" }
        return text
    }
}
