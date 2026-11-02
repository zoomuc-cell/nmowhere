package com.auratechnologies.nowhere.billing

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails

class PaywallActivity : AppCompatActivity(), BillingManager.Listener {

    private lateinit var statusText: TextView
    private lateinit var monthlyButton: Button
    private lateinit var yearlyButton: Button

    private var productDetails: ProductDetails? = null
    private var loadingPlans = false
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BillingManager.get().isPro) {
            proceedToTarget()
            return
        }

        buildUi()
        loadPlans()
    }

    override fun onStart() {
        super.onStart()
        if (navigated || isFinishing) return
        val billing = BillingManager.get()
        billing.addListener(this)
        // 화면이 멈춰 있는 동안 Pro가 되었을 수 있음
        if (billing.isPro) {
            proceedToTarget()
            return
        }
        // 연결 전에 열려 요금제를 못 불러왔다면 다시 시도
        if (productDetails == null) loadPlans()
    }

    override fun onStop() {
        BillingManager.get().removeListener(this)
        super.onStop()
    }

    private fun buildUi() {
        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 60)
        }

        container.addView(TextView(this).apply {
            text = "NOWHERE Pro"
            textSize = 28f
            setPadding(0, 0, 0, 24)
        })

        container.addView(TextView(this).apply {
            text = "• 시간순 기록(타임라인)\n" +
                    "• 저장 기록 관리\n" +
                    "• 기록 보고서 생성·내보내기\n" +
                    "• 그동안 쌓인 모든 기록을 바로 확인"
            textSize = 17f
            setLineSpacing(8f, 1f)
            setPadding(0, 0, 0, 30)
        })

        statusText = TextView(this).apply {
            text = "구독 정보를 불러오는 중…"
            textSize = 15f
            setPadding(0, 0, 0, 20)
        }
        container.addView(statusText)

        monthlyButton = Button(this).apply {
            text = "월간 구독"
            isEnabled = false
            setOnClickListener { purchase(BillingConstants.PLAN_MONTHLY) }
        }
        container.addView(monthlyButton)

        yearlyButton = Button(this).apply {
            text = "연간 구독"
            isEnabled = false
            setOnClickListener { purchase(BillingConstants.PLAN_YEARLY) }
        }
        container.addView(yearlyButton)

        container.addView(Button(this).apply {
            text = "구매 복원"
            setOnClickListener {
                BillingManager.get().refresh { pro, verified ->
                    if (pro || isFinishing) return@refresh
                    val message = if (verified) {
                        "복원할 구독이 없습니다."
                    } else {
                        "Google Play에 연결할 수 없습니다."
                    }
                    Toast.makeText(this@PaywallActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })

        container.addView(Button(this).apply {
            text = "구독 관리"
            setOnClickListener { openSubscriptionManagement() }
        })

        container.addView(TextView(this).apply {
            text = "구독은 Google Play 계정으로 결제되며, 해지하지 않으면 자동 갱신됩니다. 언제든지 Google Play에서 해지할 수 있습니다."
            textSize = 13f
            setPadding(0, 30, 0, 0)
        })

        scrollView.addView(container)
        // targetSdk 35+ 기기는 edge-to-edge가 강제되므로 상태바/내비게이션바 영역만큼 띄운다
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scrollView)
    }

    private fun loadPlans() {
        if (loadingPlans || productDetails != null) return
        loadingPlans = true
        BillingManager.get().queryPlans { result, details ->
            // 콜백은 메인 스레드에서 호출됨
            loadingPlans = false
            if (isFinishing || isDestroyed) return@queryPlans
            if (result.responseCode != BillingResponseCode.OK || details == null) {
                statusText.text = when (result.responseCode) {
                    BillingResponseCode.BILLING_UNAVAILABLE ->
                        "이 기기에서는 Google Play 결제를 사용할 수 없습니다."
                    BillingResponseCode.SERVICE_DISCONNECTED,
                    BillingResponseCode.SERVICE_UNAVAILABLE ->
                        "Google Play에 연결할 수 없습니다."
                    else ->
                        "구독 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."
                }
                return@queryPlans
            }

            productDetails = details
            val monthlyPrice = priceOf(details, BillingConstants.PLAN_MONTHLY)
            val yearlyPrice = priceOf(details, BillingConstants.PLAN_YEARLY)

            if (monthlyPrice != null) {
                monthlyButton.text = "월간 구독 · $monthlyPrice/월"
                monthlyButton.isEnabled = true
            }
            if (yearlyPrice != null) {
                yearlyButton.text = "연간 구독 · $yearlyPrice/년"
                yearlyButton.isEnabled = true
            }

            statusText.text = if (monthlyPrice == null && yearlyPrice == null) {
                "구독 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."
            } else {
                ""
            }
        }
    }

    /** 기본 요금제(offerId == null)의 마지막 가격 단계 표시 가격. */
    private fun priceOf(details: ProductDetails, basePlanId: String): String? =
        details.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == basePlanId && it.offerId == null }
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice

    private fun purchase(basePlanId: String) {
        val details = productDetails ?: return
        val result = BillingManager.get().launchPurchase(this, details, basePlanId)
        // 실패 알림은 onPurchaseError로 한 번만 전달되므로 여기서는 토스트하지 않음
        if (result.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED) {
            BillingManager.get().refresh()
        }
    }

    private fun showPurchaseError() {
        Toast.makeText(this, "결제 중 오류가 발생했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
    }

    private fun openSubscriptionManagement() {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                    "?sku=${BillingConstants.PRODUCT_ID}&package=$packageName"
        )
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "구독 관리 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Pro 상태가 되면 원래 가려던 화면으로 이동하고 종료. 한 번만 실행. */
    private fun proceedToTarget() {
        if (navigated) return
        navigated = true
        val target = IntentCompat.getParcelableExtra(intent, EXTRA_TARGET_INTENT, Intent::class.java)
        if (target != null) startActivity(target)
        finish()
    }

    override fun onProChanged(isPro: Boolean) {
        if (isPro && !isFinishing) proceedToTarget()
    }

    override fun onPurchasePending() {
        if (!::statusText.isInitialized) return
        statusText.text = "결제 확인 중입니다. 완료되면 자동으로 Pro가 활성화됩니다."
    }

    override fun onPurchaseError(result: BillingResult) {
        showPurchaseError()
    }

    override fun onConnected() {
        if (navigated || isFinishing || !::statusText.isInitialized) return
        if (productDetails == null) loadPlans()
    }

    companion object {
        const val EXTRA_TARGET_INTENT = "target_intent"
    }
}
