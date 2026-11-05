import Foundation
import StoreKit

// Android billing/BillingManager.kt + ProStatus.kt 대응 (App Store, StoreKit 2).
// 서버 없이 기기에서 App Store가 서명한 거래(Transaction)만 확인한다.

enum ProConstants {
    /// App Store Connect의 구독 그룹 "NOWHERE Pro"에 만들 자동 갱신 구독 상품 ID
    static let monthlyID = "nowhere_pro_monthly"
    static let yearlyID = "nowhere_pro_yearly"
    static let productIDs: Set<String> = [monthlyID, yearlyID]

    /// App Store 확인이 안 될 때(오프라인 등) 마지막 확인 결과를 믿어 주는 기간 (Android와 같은 7일)
    static let offlineGrace: TimeInterval = 7 * 24 * 60 * 60

    static let privacyPolicyURL = URL(string: "https://zoomuc-cell.github.io/nowhere-privacy/")!
    static let termsOfUseURL = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!
}

extension Notification.Name {
    static let proStatusChanged = Notification.Name("NOWHEREProStatusChanged")
}

final class ProStore {
    static let shared = ProStore()

    enum PurchaseOutcome {
        case success, pending, cancelled, failed
    }

    private let defaults = UserDefaults.standard
    private let cachedKey = "pro.isPro"
    private let verifiedAtKey = "pro.lastVerifiedAt"
    private var updatesTask: Task<Void, Never>?

    private(set) var products: [Product] = []

    private init() {}

    /// 앱 시작 시 한 번 호출: 결제 갱신·환불 등 App Store 쪽 변화를 듣고 현재 구독을 확인한다.
    func start() {
        guard updatesTask == nil else { return }
        updatesTask = Task.detached { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let transaction) = result {
                    await transaction.finish()
                }
                await self?.refresh()
            }
        }
        Task { await refresh() }
    }

    /// 지금 Pro인지 (화면 이동 판단용, 즉시 응답).
    var isPro: Bool {
        guard defaults.bool(forKey: cachedKey) else { return false }
        let verifiedAt = defaults.double(forKey: verifiedAtKey)
        return Date().timeIntervalSince1970 - verifiedAt <= ProConstants.offlineGrace
    }

    /// App Store에 저장된 현재 구독 권한을 다시 확인한다. 확인 결과(Pro 여부)를 돌려준다.
    @discardableResult
    func refresh() async -> Bool {
        var active = false
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result,
                  ProConstants.productIDs.contains(transaction.productID),
                  transaction.revocationDate == nil else { continue }
            if let expiration = transaction.expirationDate, expiration < Date() { continue }
            active = true
        }
        await MainActor.run { self.save(isPro: active) }
        return active
    }

    func loadProducts() async throws -> [Product] {
        let loaded = try await Product.products(for: Array(ProConstants.productIDs))
        let sorted = loaded.sorted { $0.price < $1.price }
        await MainActor.run { self.products = sorted }
        return sorted
    }

    func purchase(_ product: Product) async -> PurchaseOutcome {
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                guard case .verified(let transaction) = verification else { return .failed }
                await transaction.finish()
                await refresh()
                return .success
            case .pending:
                return .pending
            case .userCancelled:
                return .cancelled
            @unknown default:
                return .failed
            }
        } catch {
            return .failed
        }
    }

    /// "구매 복원": App Store와 동기화한 뒤 다시 확인한다. 동기화 실패 시 nil.
    func restore() async -> Bool? {
        do {
            try await AppStore.sync()
        } catch {
            return nil
        }
        return await refresh()
    }

    @MainActor
    private func save(isPro: Bool) {
        let changed = self.isPro != isPro
        defaults.set(isPro, forKey: cachedKey)
        defaults.set(Date().timeIntervalSince1970, forKey: verifiedAtKey)
        if changed {
            NotificationCenter.default.post(name: .proStatusChanged, object: nil)
        }
    }
}
