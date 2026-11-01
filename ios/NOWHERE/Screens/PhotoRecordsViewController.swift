import UIKit
import ImageIO

/// Android PhotoRecordsActivity.kt 대응: 촬영 사진 목록과 크게 보기.
final class PhotoRecordsViewController: UIViewController {

    private let photoDatabase = PhotoDatabase.shared

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "촬영 사진 기록"
        view.backgroundColor = UIColor(red: 245 / 255, green: 247 / 255, blue: 249 / 255, alpha: 1)

        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 0
        stack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            stack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            stack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -16),
            stack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16)
        ])

        let titleLabel = makeLabel("촬영 사진 기록", size: 26,
                                   color: UIColor(red: 25 / 255, green: 35 / 255, blue: 45 / 255, alpha: 1))
        stack.addArrangedSubview(titleLabel)
        stack.setCustomSpacing(4, after: titleLabel)

        let countLabel = makeLabel("저장된 현장 사진 \(photoDatabase.getTotalRecords())장", size: 15, color: .darkGray)
        stack.addArrangedSubview(countLabel)
        stack.setCustomSpacing(16, after: countLabel)

        let records = photoDatabase.getAll().sorted { $0.timestamp > $1.timestamp }

        if records.isEmpty {
            let empty = makeLabel("아직 촬영된 사진이 없습니다.\n\n홈 화면에서 '현장 사진 촬영'을 선택해 사진을 기록해 주세요.",
                                  size: 17, color: .gray)
            empty.textAlignment = .center
            let wrapper = UIView()
            empty.translatesAutoresizingMaskIntoConstraints = false
            wrapper.addSubview(empty)
            NSLayoutConstraint.activate([
                empty.topAnchor.constraint(equalTo: wrapper.topAnchor, constant: 60),
                empty.bottomAnchor.constraint(equalTo: wrapper.bottomAnchor, constant: -60),
                empty.leadingAnchor.constraint(equalTo: wrapper.leadingAnchor, constant: 10),
                empty.trailingAnchor.constraint(equalTo: wrapper.trailingAnchor, constant: -10)
            ])
            stack.addArrangedSubview(wrapper)
        } else {
            for record in records {
                stack.addArrangedSubview(makeCard(record))
            }
        }
    }

    private func makeLabel(_ text: String, size: CGFloat, color: UIColor) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: size)
        label.textColor = color
        label.numberOfLines = 0
        return label
    }

    private func makeCard(_ record: PhotoRecord) -> UIView {
        let card = UIStackView()
        card.axis = .vertical
        card.spacing = 10
        card.backgroundColor = .white
        card.isLayoutMarginsRelativeArrangement = true
        card.layoutMargins = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)

        let imageView = UIImageView(image: thumbnail(record.photoFileURL, maxPixel: 1000))
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.backgroundColor = .lightGray
        imageView.heightAnchor.constraint(equalToConstant: 260).isActive = true
        imageView.isUserInteractionEnabled = true
        imageView.addGestureRecognizer(PhotoTapGesture(record: record, target: self, action: #selector(showLargePhoto(_:))))
        card.addArrangedSubview(imageView)

        let information = """
        촬영시각  \(Format.date(record.timestamp, "yyyy년 MM월 dd일 HH:mm:ss"))

        위치
        위도  \(Format.fixed(record.latitude, 6))
        경도  \(Format.fixed(record.longitude, 6))
        고도  \(Format.fixed(record.altitude, 1)) m

        GPS 정확도  ±\(Format.int(record.locationAccuracy)) m

        촬영방향  \(Format.fixed(record.azimuth, 1))° · \(Direction.name(record.azimuth))
        방향센서 정확도  \(Direction.accuracyText(record.directionAccuracy))

        이동상태  \(record.transportMode)

        사진을 누르면 크게 볼 수 있습니다.
        """
        card.addArrangedSubview(makeLabel(information, size: 15,
                                          color: UIColor(red: 40 / 255, green: 45 / 255, blue: 50 / 255, alpha: 1)))

        let container = UIStackView(arrangedSubviews: [card])
        container.axis = .vertical
        container.isLayoutMarginsRelativeArrangement = true
        container.layoutMargins = UIEdgeInsets(top: 0, left: 0, bottom: 20, right: 0)

        let divider = UIView()
        divider.backgroundColor = UIColor(red: 225 / 255, green: 228 / 255, blue: 232 / 255, alpha: 1)
        divider.heightAnchor.constraint(equalToConstant: 1.5).isActive = true
        container.addArrangedSubview(divider)
        container.setCustomSpacing(6, after: card)

        return container
    }

    /// 목록에는 줄인 이미지를 써서 사진이 많아도 메모리가 넘치지 않게 한다.
    private func thumbnail(_ url: URL, maxPixel: Int) -> UIImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: image)
    }

    @objc private func showLargePhoto(_ sender: UITapGestureRecognizer) {
        guard let gesture = sender as? PhotoTapGesture else { return }
        let viewer = LargePhotoViewController(image: UIImage(contentsOfFile: gesture.record.photoFileURL.path))
        present(viewer, animated: true)
    }
}

private final class PhotoTapGesture: UITapGestureRecognizer {
    let record: PhotoRecord

    init(record: PhotoRecord, target: Any?, action: Selector?) {
        self.record = record
        super.init(target: target, action: action)
    }
}

/// 검은 배경에 사진을 화면 크기에 맞춰 보여주고, 누르면 닫는다.
private final class LargePhotoViewController: UIViewController {
    private let image: UIImage?

    init(image: UIImage?) {
        self.image = image
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
        modalTransitionStyle = .crossDissolve
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        let imageView = UIImageView(image: image)
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            imageView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            imageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])

        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(close)))
    }

    override var prefersStatusBarHidden: Bool { true }

    @objc private func close() {
        dismiss(animated: true)
    }
}
