package com.auratechnologies.nowhere

import android.app.Application
import com.auratechnologies.nowhere.billing.BillingManager

class NowhereApp : Application() {

    override fun onCreate() {
        super.onCreate()
        BillingManager.init(this).refresh()
    }
}
