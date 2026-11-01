import UIKit

/// Android RecordManagementActivity.kt 대응: 날짜별 기록 요약과 바로가기.
final class RecordManagementViewController: UIViewController {

    private let stack = UIStackView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "저장 기록 관리"
        view.backgroundColor = .systemBackground

        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        stack.axis = .vertical
        stack.spacing = 0
        stack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            stack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 24),
            stack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -30),
            stack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -20)
        ])

        buildContent()
    }

    private func buildContent() {
        let movements = MovementDatabase.shared.getAllRecords()
        let photos = PhotoDatabase.shared.getAll()

        addLabel("🗂 저장 기록 관리", size: 28, after: 6)
        addLabel("날짜별로 저장된 현장 기록을 확인합니다.", size: 16, after: 16)

        let summary = PaddedLabel(text:
            "전체 저장 기록\n\n" +
            "📍 이동 위치 기록: \(movements.count)건\n" +
            "📷 촬영 사진 기록: \(photos.count)장",
            size: 18, background: .clear)
        stack.addArrangedSubview(summary)

        addLabel("날짜별 기록", size: 22, before: 20, after: 10)

        let dayPattern = "yyyy.MM.dd"
        let timePattern = "HH:mm:ss"

        var allDates = Set<String>()
        movements.forEach { allDates.insert(Format.date($0.timestamp, dayPattern)) }
        photos.forEach { allDates.insert(Format.date($0.timestamp, dayPattern)) }
        let sortedDates = allDates.sorted(by: >)

        if sortedDates.isEmpty {
            stack.addArrangedSubview(PaddedLabel(text: "아직 저장된 기록이 없습니다.", size: 17, background: .clear))
        } else {
            for dateText in sortedDates {
                let dayMovements = movements.filter { Format.date($0.timestamp, dayPattern) == dateText }
                let dayPhotos = photos.filter { Format.date($0.timestamp, dayPattern) == dateText }

                let timestamps = dayMovements.map { $0.timestamp } + dayPhotos.map { $0.timestamp }
                let startTime = timestamps.min()
                let endTime = timestamps.max()

                let periodText: String
                if let start = startTime, let end = endTime {
                    periodText = Format.date(start, timePattern) + " ~ " + Format.date(end, timePattern)
                } else {
                    periodText = "-"
                }

                let card = PaddedLabel(text:
                    "📅 \(dateText)\n\n" +
                    "📍 이동 기록  \(dayMovements.count)건\n" +
                    "📷 사진 기록  \(dayPhotos.count)장\n" +
                    "⏱ \(periodText)",
                    size: 18,
                    background: UIColor(red: 0xF1 / 255, green: 0xF3 / 255, blue: 0xF6 / 255, alpha: 1))

                card.onTap = { [weak self] in
                    guard let start = startTime, let end = endTime else { return }
                    self?.push(ReportViewController.make(startTime: start, endTime: end))
                }

                stack.addArrangedSubview(card)
                stack.setCustomSpacing(10, after: card)
            }
        }

        addLabel("전체 기록 바로가기", size: 22, before: 20, after: 8)

        addButton("📷 촬영 사진 기록 보기") { [weak self] in
            self?.push(PhotoRecordsViewController())
        }
        addButton("🌐 통합 기록 지도 보기") { [weak self] in
            self?.push(Screens.integratedMap())
        }
        addButton("⏱ 시간순 기록 보기") { [weak self] in
            self?.push(Screens.timeline())
        }
        addButton("📄 기록 보고서 / PDF") { [weak self] in
            self?.push(ReportViewController.make())
        }
    }

    private func push(_ controller: UIViewController?) {
        guard let controller = controller else { return }
        navigationController?.pushViewController(controller, animated: true)
    }

    private func addLabel(_ text: String, size: CGFloat, before: CGFloat = 0, after: CGFloat) {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: size)
        label.numberOfLines = 0
        if before > 0, let last = stack.arrangedSubviews.last {
            stack.setCustomSpacing(before, after: last)
        }
        stack.addArrangedSubview(label)
        stack.setCustomSpacing(after, after: label)
    }

    private func addButton(_ title: String, action: @escaping () -> Void) {
        var configuration = UIButton.Configuration.gray()
        configuration.title = title
        configuration.baseForegroundColor = .label
        let button = UIButton(configuration: configuration, primaryAction: UIAction { _ in action() })
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        stack.addArrangedSubview(button)
        stack.setCustomSpacing(8, after: button)
    }
}

/// 안쪽 여백과 배경색이 있고, 누를 수 있는 글 상자 (Android TextView + setOnClickListener 대응)
private final class PaddedLabel: UIView {
    var onTap: (() -> Void)? {
        didSet { isUserInteractionEnabled = onTap != nil }
    }

    init(text: String, size: CGFloat, background: UIColor) {
        super.init(frame: .zero)
        backgroundColor = background
        layer.cornerRadius = 6

        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: size)
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        addSubview(label)
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: topAnchor, constant: 15),
            label.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -15),
            label.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 17),
            label.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -17)
        ])

        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapped)))
        isUserInteractionEnabled = false
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    @objc private func tapped() {
        onTap?()
    }
}
