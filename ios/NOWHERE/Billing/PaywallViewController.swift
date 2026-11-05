import UIKit
import StoreKit

/// Android billing/PaywallActivity.kt + ProGate.kt 대응.
/// Pro 전용 화면(시간순 기록, 보고서, 저장 기록 관리)을 누르면 구독하지 않은 경우 이 화면이 먼저 뜬다.
enum ProGate {
    /// Pro면 makeTarget()이 만든 화면을 열고, 아니면 결제 화면을 연다.
    /// 결제가 끝나면 결제 화면이 목표 화면으로 바뀐다.
    static func open(from navigation: UINavigationController?,
                     _ makeTarget: @escaping () -> UIViewController?) {
        guard let navigation = navigation else { return }
        if ProStore.shared.isPro {
            if let target = makeTarget() {
                navigation.pushViewController(target, animated: true)
            }
            return
        }
        navigation.pushViewController(PaywallViewController(makeTarget: makeTarget), animated: true)
    }
}

final class PaywallViewController: UIViewController {

    private let makeTarget: () -> UIViewController?
    private var navigated = false
    private var loading = false
    private var monthly: Product?
    private var yearly: Product?

    private let statusLabel = UILabel()
    private let monthlyButton = UIButton(type: .system)
    private let yearlyButton = UIButton(type: .system)
    private let restoreButton = UIButton(type: .system)
    private let manageButton = UIButton(type: .system)

    init(makeTarget: @escaping () -> UIViewController?) {
        self.makeTarget = makeTarget
        super.init(nibName: nil, bundle: nil)
        title = "NOWHERE Pro"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        buildUI()
        NotificationCenter.default.addObserver(self, selector: #selector(proChanged),
                                               name: .proStatusChanged, object: nil)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // 화면이 가려진 동안 Pro가 되었을 수 있음
        if ProStore.shared.isPro {
            proceedToTarget()
            return
        }
        if monthly == nil && yearly == nil { loadPlans() }
    }

    // MARK: - 화면 구성

    private func buildUI() {
        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 28),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -40),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -24)
        ])

        let titleLabel = label("NOWHERE Pro", size: 28, weight: .bold)
        stack.addArrangedSubview(titleLabel)

        let benefits = label("• 시간순 기록(타임라인)\n• 저장 기록 관리\n• 기록 보고서 생성·내보내기\n• 그동안 쌓인 모든 기록을 바로 확인",
                             size: 17, weight: .regular)
        stack.addArrangedSubview(benefits)
        stack.setCustomSpacing(24, after: benefits)

        statusLabel.text = "구독 정보를 불러오는 중…"
        statusLabel.font = .systemFont(ofSize: 15)
        statusLabel.textColor = .secondaryLabel
        statusLabel.numberOfLines = 0
        stack.addArrangedSubview(statusLabel)

        configure(monthlyButton, title: "월간 구독", filled: true, action: #selector(buyMonthly))
        configure(yearlyButton, title: "연간 구독", filled: true, action: #selector(buyYearly))
        configure(restoreButton, title: "구매 복원", filled: false, action: #selector(restore))
        configure(manageButton, title: "구독 관리", filled: false, action: #selector(manage))
        monthlyButton.isEnabled = false
        yearlyButton.isEnabled = false
        [monthlyButton, yearlyButton, restoreButton, manageButton].forEach { stack.addArrangedSubview($0) }

        let notice = label("구독은 Apple ID 계정으로 결제되며, 현재 기간이 끝나기 최소 24시간 전에 해지하지 않으면 같은 기간·가격으로 자동 갱신됩니다. 구독 관리와 해지는 '구독 관리' 버튼이나 iPhone 설정 > Apple ID > 구독에서 할 수 있습니다. 기록 데이터는 구독 여부와 관계없이 기기에 그대로 남습니다.",
                           size: 13, weight: .regular)
        notice.textColor = .secondaryLabel
        stack.setCustomSpacing(24, after: manageButton)
        stack.addArrangedSubview(notice)

        let links = UIStackView()
        links.axis = .horizontal
        links.distribution = .fillEqually
        let privacy = UIButton(type: .system)
        privacy.setTitle("개인정보처리방침", for: .normal)
        privacy.addTarget(self, action: #selector(openPrivacy), for: .touchUpInside)
        let terms = UIButton(type: .system)
        terms.setTitle("이용약관(EULA)", for: .normal)
        terms.addTarget(self, action: #selector(openTerms), for: .touchUpInside)
        links.addArrangedSubview(privacy)
        links.addArrangedSubview(terms)
        stack.addArrangedSubview(links)
    }

    private func label(_ text: String, size: CGFloat, weight: UIFont.Weight) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: size, weight: weight)
        label.numberOfLines = 0
        return label
    }

    private func configure(_ button: UIButton, title: String, filled: Bool, action: Selector) {
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 17, weight: filled ? .semibold : .regular)
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: 50).isActive = true
        button.layer.cornerRadius = 12
        if filled {
            button.backgroundColor = UIColor(red: 0x66 / 255, green: 0x72 / 255, blue: 0x7e / 255, alpha: 1)
            button.setTitleColor(.white, for: .normal)
            button.setTitleColor(UIColor(white: 1, alpha: 0.5), for: .disabled)
        } else {
            button.layer.borderWidth = 1
            button.layer.borderColor = UIColor.separator.cgColor
        }
        button.addTarget(self, action: action, for: .touchUpInside)
    }

    // MARK: - 요금제

    private func loadPlans() {
        guard !loading else { return }
        loading = true
        statusLabel.text = "구독 정보를 불러오는 중…"
        Task { @MainActor in
            defer { loading = false }
            do {
                let products = try await ProStore.shared.loadProducts()
                monthly = products.first { $0.id == ProConstants.monthlyID }
                yearly = products.first { $0.id == ProConstants.yearlyID }
            } catch {
                monthly = nil
                yearly = nil
            }
            if let monthly = monthly {
                monthlyButton.setTitle("월간 구독 · \(monthly.displayPrice)/월", for: .normal)
                monthlyButton.isEnabled = true
            }
            if let yearly = yearly {
                yearlyButton.setTitle("연간 구독 · \(yearly.displayPrice)/년", for: .normal)
                yearlyButton.isEnabled = true
            }
            statusLabel.text = (monthly == nil && yearly == nil)
                ? "구독 정보를 불러올 수 없습니다. 인터넷 연결을 확인한 뒤 다시 열어 주세요."
                : ""
        }
    }

    @objc private func buyMonthly() { buy(monthly) }
    @objc private func buyYearly() { buy(yearly) }

    private func buy(_ product: Product?) {
        guard let product = product else { return }
        setButtonsEnabled(false)
        Task { @MainActor in
            let outcome = await ProStore.shared.purchase(product)
            setButtonsEnabled(true)
            switch outcome {
            case .success:
                proceedToTarget()
            case .pending:
                statusLabel.text = "결제 확인 중입니다. 승인되면 자동으로 Pro가 활성화됩니다."
            case .cancelled:
                break
            case .failed:
                Toast.show("결제 중 오류가 발생했습니다. 다시 시도해 주세요.")
            }
        }
    }

    @objc private func restore() {
        setButtonsEnabled(false)
        Task { @MainActor in
            let result = await ProStore.shared.restore()
            setButtonsEnabled(true)
            switch result {
            case .some(true):
                proceedToTarget()
            case .some(false):
                Toast.show("복원할 구독이 없습니다.")
            case .none:
                Toast.show("App Store에 연결할 수 없습니다.")
            }
        }
    }

    @objc private func manage() {
        guard let scene = view.window?.windowScene else { return }
        Task { @MainActor in
            do {
                try await AppStore.showManageSubscriptions(in: scene)
            } catch {
                Toast.show("구독 관리 화면을 열 수 없습니다.")
            }
            await ProStore.shared.refresh()
        }
    }

    @objc private func openPrivacy() {
        UIApplication.shared.open(ProConstants.privacyPolicyURL)
    }

    @objc private func openTerms() {
        UIApplication.shared.open(ProConstants.termsOfUseURL)
    }

    private func setButtonsEnabled(_ enabled: Bool) {
        monthlyButton.isEnabled = enabled && monthly != nil
        yearlyButton.isEnabled = enabled && yearly != nil
        restoreButton.isEnabled = enabled
    }

    // MARK: - Pro가 되면 원래 화면으로

    @objc private func proChanged() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, ProStore.shared.isPro, self.view.window != nil else { return }
            self.proceedToTarget()
        }
    }

    /// 결제 화면을 목표 화면으로 바꾼다. 한 번만 실행.
    private func proceedToTarget() {
        guard !navigated, let navigation = navigationController else { return }
        navigated = true
        var stack = navigation.viewControllers
        stack.removeAll { $0 === self }
        if let target = makeTarget() {
            stack.append(target)
        }
        navigation.setViewControllers(stack, animated: true)
    }
}
