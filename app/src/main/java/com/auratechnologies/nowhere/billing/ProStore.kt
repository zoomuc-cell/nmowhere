package com.auratechnologies.nowhere.billing

import android.content.Context

class ProStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ProCache = ProCache(
        isPro = prefs.getBoolean(KEY_IS_PRO, false),
        lastVerifiedAtMs = prefs.getLong(KEY_LAST_VERIFIED_AT, 0L)
    )

    fun save(cache: ProCache) {
        prefs.edit()
            .putBoolean(KEY_IS_PRO, cache.isPro)
            .putLong(KEY_LAST_VERIFIED_AT, cache.lastVerifiedAtMs)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "nowhere_pro"
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_LAST_VERIFIED_AT = "last_verified_at"
    }
}
