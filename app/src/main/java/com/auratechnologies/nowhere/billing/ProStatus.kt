package com.auratechnologies.nowhere.billing

enum class PurchaseState { PURCHASED, PENDING, OTHER }

data class PurchaseSnapshot(
    val productIds: List<String>,
    val state: PurchaseState,
    val acknowledged: Boolean,
    val purchaseToken: String
)

sealed class QueryOutcome {
    data class Success(val purchases: List<PurchaseSnapshot>) : QueryOutcome()
    object Failure : QueryOutcome()
    object NotYetQueried : QueryOutcome()
}

data class ProCache(val isPro: Boolean, val lastVerifiedAtMs: Long)

data class ProDecision(
    val isPro: Boolean,
    val cacheToSave: ProCache?,
    val tokensToAcknowledge: List<String>
)

object ProStatus {
    fun decide(outcome: QueryOutcome, cache: ProCache, nowMs: Long): ProDecision = when (outcome) {
        is QueryOutcome.Success -> {
            val active = outcome.purchases.filter {
                it.state == PurchaseState.PURCHASED && BillingConstants.PRODUCT_ID in it.productIds
            }
            val isPro = active.isNotEmpty()
            ProDecision(
                isPro = isPro,
                cacheToSave = ProCache(isPro, nowMs),
                tokensToAcknowledge = active.filter { !it.acknowledged }.map { it.purchaseToken }
            )
        }
        QueryOutcome.Failure, QueryOutcome.NotYetQueried -> ProDecision(
            isPro = cache.isPro && (nowMs - cache.lastVerifiedAtMs) <= BillingConstants.OFFLINE_GRACE_MS,
            cacheToSave = null,
            tokensToAcknowledge = emptyList()
        )
    }
}
