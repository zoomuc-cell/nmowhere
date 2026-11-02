package com.auratechnologies.nowhere.billing

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.CopyOnWriteArrayList

class BillingManager private constructor(app: Application) {

    interface Listener {
        fun onProChanged(isPro: Boolean)
        fun onPurchasePending()
        /**
         * 구매 실패. Play가 PurchasesUpdatedListener로 전달한 실패와, launchPurchase가
         * Play를 호출하지 않고 만든 실패(연결 안 됨·요금제 없음)가 모두 여기로 한 번씩 전달됨.
         * ITEM_ALREADY_OWNED·USER_CANCELED는 전달되지 않음.
         */
        fun onPurchaseError(result: BillingResult)

        /** Play 결제 서비스 연결 완료(onBillingSetupFinished OK). 메인 스레드에서 호출. */
        fun onConnected() {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val store = ProStore(app)
    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    private var currentIsPro: Boolean =
        ProStatus.decide(QueryOutcome.NotYetQueried, store.load(), System.currentTimeMillis()).isPro

    /** 현재 Pro 판정. 블로킹 없이 즉시 반환. */
    val isPro: Boolean
        get() = currentIsPro

    // 아래 연결 상태 필드는 메인 스레드에서만 접근
    private var connecting = false
    private var retryScheduled = false
    private var retryDelayMs = INITIAL_RETRY_DELAY_MS
    private var lastSetupResponseCode = BillingResponseCode.SERVICE_DISCONNECTED

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        mainHandler.post { handlePurchasesUpdated(result, purchases.orEmpty()) }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(app)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        mainHandler.post { startConnection() }
    }

    fun addListener(l: Listener) {
        listeners.addIfAbsent(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    /**
     * Play에서 구독을 조회해 판정·캐시 저장·승인. onDone은 메인 스레드에서 호출.
     * verified는 Play가 실제로 응답(OK)했는지 여부. false면 isPro는 캐시 기반 판정.
     */
    fun refresh(onDone: ((isPro: Boolean, verified: Boolean) -> Unit)? = null) {
        if (!billingClient.isReady) {
            mainHandler.post {
                applyDecision(QueryOutcome.Failure)
                onDone?.invoke(currentIsPro, false)
            }
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            mainHandler.post {
                val verified = result.responseCode == BillingResponseCode.OK
                val outcome = if (verified) {
                    QueryOutcome.Success(purchases.map(::toSnapshot))
                } else {
                    Log.w(TAG, "queryPurchases failed: ${result.responseCode} ${result.debugMessage}")
                    QueryOutcome.Failure
                }
                applyDecision(outcome)
                onDone?.invoke(currentIsPro, verified)
            }
        }
    }

    /**
     * nowhere_pro 상품 정보 조회. onResult는 메인 스레드에서 호출.
     * 연결 전이면 마지막 연결 결과 코드(예: BILLING_UNAVAILABLE)를 돌려줌.
     */
    fun queryPlans(onResult: (BillingResult, ProductDetails?) -> Unit) {
        if (!billingClient.isReady) {
            mainHandler.post {
                val code = if (lastSetupResponseCode == BillingResponseCode.OK) {
                    BillingResponseCode.SERVICE_DISCONNECTED
                } else {
                    lastSetupResponseCode
                }
                val result = BillingResult.newBuilder()
                    .setResponseCode(code)
                    .setDebugMessage("BillingClient is not connected")
                    .build()
                onResult(result, null)
            }
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BillingConstants.PRODUCT_ID)
            .setProductType(ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            val details = queryResult.productDetailsList
                .firstOrNull { it.productId == BillingConstants.PRODUCT_ID }
            mainHandler.post { onResult(result, details) }
        }
    }

    /**
     * 기본 요금제(offerId == null)로 결제 화면 시작. 메인 스레드에서 호출.
     * 실패 알림은 리스너 onPurchaseError로만 전달됨(Play가 보낸 것이든, 여기서 만든 것이든).
     * 호출자는 반환값으로 오류를 다시 표시하지 말 것. ITEM_ALREADY_OWNED만 호출자가 처리.
     */
    fun launchPurchase(activity: Activity, details: ProductDetails, basePlanId: String): BillingResult {
        if (!billingClient.isReady) {
            return localPurchaseError(
                BillingResponseCode.SERVICE_DISCONNECTED, "BillingClient is not connected"
            )
        }
        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == basePlanId && it.offerId == null }
            ?.offerToken
            ?: return localPurchaseError(
                BillingResponseCode.DEVELOPER_ERROR,
                "No base plan offer for '$basePlanId' in ${details.productId}"
            )
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        return billingClient.launchBillingFlow(activity, flowParams)
    }

    /** Play를 거치지 않은 구매 실패: Play가 리스너로 보내지 않으므로 직접 리스너에 전달. */
    private fun localPurchaseError(code: Int, message: String): BillingResult {
        val result = BillingResult.newBuilder()
            .setResponseCode(code)
            .setDebugMessage(message)
            .build()
        Log.w(TAG, "Purchase not launched: $code $message")
        mainHandler.post { listeners.forEach { it.onPurchaseError(result) } }
        return result
    }

    private fun startConnection() {
        if (billingClient.isReady || connecting) return
        connecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                mainHandler.post {
                    connecting = false
                    lastSetupResponseCode = result.responseCode
                    when (result.responseCode) {
                        BillingResponseCode.OK -> {
                            retryDelayMs = INITIAL_RETRY_DELAY_MS
                            refresh()
                            listeners.forEach { it.onConnected() }
                        }
                        // Play 결제가 없는 기기: 재시도해도 소용없으므로 중단
                        BillingResponseCode.BILLING_UNAVAILABLE,
                        BillingResponseCode.FEATURE_NOT_SUPPORTED ->
                            Log.w(TAG, "Billing unavailable: ${result.responseCode} ${result.debugMessage}")
                        else -> {
                            Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                            scheduleReconnect()
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                mainHandler.post {
                    connecting = false
                    lastSetupResponseCode = BillingResponseCode.SERVICE_DISCONNECTED
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (retryScheduled) return
        retryScheduled = true
        val delay = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        mainHandler.postDelayed({
            retryScheduled = false
            startConnection()
        }, delay)
    }

    private fun handlePurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val snapshots = purchases.map(::toSnapshot)
                if (snapshots.any { it.state == PurchaseState.PURCHASED }) {
                    applyDecision(QueryOutcome.Success(snapshots))
                } else {
                    // 이번 흐름의 구매만 담긴 목록이므로, 확정 구매가 없으면
                    // 전체 조회로 판정해 기존 Pro를 잘못 해제하지 않도록 함
                    refresh()
                }
                if (snapshots.isNotEmpty() && snapshots.all { it.state == PurchaseState.PENDING }) {
                    listeners.forEach { it.onPurchasePending() }
                }
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                listeners.forEach { it.onPurchaseError(result) }
            }
        }
    }

    // 메인 스레드에서 호출
    private fun applyDecision(outcome: QueryOutcome) {
        val decision = ProStatus.decide(outcome, store.load(), System.currentTimeMillis())
        decision.cacheToSave?.let { store.save(it) }
        decision.tokensToAcknowledge.forEach { acknowledge(it) }
        setPro(decision.isPro)
    }

    private fun acknowledge(token: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(token)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "acknowledgePurchase failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun setPro(value: Boolean) {
        if (value == currentIsPro) return
        currentIsPro = value
        listeners.forEach { it.onProChanged(value) }
    }

    private fun toSnapshot(purchase: Purchase): PurchaseSnapshot = PurchaseSnapshot(
        productIds = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.OTHER
        },
        acknowledged = purchase.isAcknowledged,
        purchaseToken = purchase.purchaseToken
    )

    companion object {
        private const val TAG = "BillingManager"
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L

        @Volatile
        private var instance: BillingManager? = null

        fun init(app: Application): BillingManager =
            instance ?: synchronized(this) {
                instance ?: BillingManager(app).also { instance = it }
            }

        fun get(): BillingManager =
            instance ?: throw IllegalStateException("BillingManager.init() must be called first")
    }
}
