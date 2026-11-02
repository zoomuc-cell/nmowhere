package com.auratechnologies.nowhere.billing

import android.app.Activity
import android.content.Intent

object ProGate {

    /** Pro면 target을 시작하고 true. 아니면 PaywallActivity(target 전달, FLAG_ACTIVITY_SINGLE_TOP)를 열고 false. */
    fun open(activity: Activity, target: Intent): Boolean {
        if (BillingManager.get().isPro) {
            activity.startActivity(target)
            return true
        }
        activity.startActivity(paywallIntent(activity, target))
        return false
    }

    /** 잠긴 Activity의 onCreate용. 미구독이면 자기 intent를 target으로 결제 화면을 열고 finish() 후 true. */
    fun redirectIfLocked(activity: Activity): Boolean {
        if (BillingManager.get().isPro) return false
        activity.startActivity(paywallIntent(activity, Intent(activity.intent)))
        activity.finish()
        return true
    }

    private fun paywallIntent(activity: Activity, target: Intent): Intent =
        Intent(activity, PaywallActivity::class.java)
            .putExtra(PaywallActivity.EXTRA_TARGET_INTENT, target)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
