package com.auratechnologies.nowhere.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProStatusTest {
    private val now = 1_000_000_000_000L
    private val DAY = 86_400_000L

    @Test fun purchasedSubscriptionIsPro() {
        val p = PurchaseSnapshot(listOf("nowhere_pro"), PurchaseState.PURCHASED, true, "t1")
        val d = ProStatus.decide(QueryOutcome.Success(listOf(p)), ProCache(false, 0), now)
        assertTrue(d.isPro); assertEquals(ProCache(true, now), d.cacheToSave); assertEquals(emptyList<String>(), d.tokensToAcknowledge)
    }

    @Test fun unacknowledgedPurchaseIsProAndNeedsAck() {
        val p = PurchaseSnapshot(listOf("nowhere_pro"), PurchaseState.PURCHASED, false, "t1")
        val d = ProStatus.decide(QueryOutcome.Success(listOf(p)), ProCache(false, 0), now)
        assertTrue(d.isPro); assertEquals(listOf("t1"), d.tokensToAcknowledge)
    }

    @Test fun pendingPurchaseIsNotPro() {
        val p = PurchaseSnapshot(listOf("nowhere_pro"), PurchaseState.PENDING, false, "t1")
        val d = ProStatus.decide(QueryOutcome.Success(listOf(p)), ProCache(false, 0), now)
        assertFalse(d.isPro); assertEquals(ProCache(false, now), d.cacheToSave); assertEquals(emptyList<String>(), d.tokensToAcknowledge)
    }

    @Test fun otherProductIsIgnored() {
        val p = PurchaseSnapshot(listOf("something_else"), PurchaseState.PURCHASED, false, "t1")
        val d = ProStatus.decide(QueryOutcome.Success(listOf(p)), ProCache(false, 0), now)
        assertFalse(d.isPro); assertEquals(emptyList<String>(), d.tokensToAcknowledge)
    }

    @Test fun successWithNoPurchasesRevokesCachedPro() {
        val d = ProStatus.decide(QueryOutcome.Success(emptyList()), ProCache(true, now - DAY), now)
        assertFalse(d.isPro); assertEquals(ProCache(false, now), d.cacheToSave)
    }

    @Test fun failureUsesCacheWithinGrace() {
        val d = ProStatus.decide(QueryOutcome.Failure, ProCache(true, now - 7 * DAY), now)
        assertTrue(d.isPro); assertNull(d.cacheToSave)
    }

    @Test fun failureRevokesAfterGrace() {
        val d = ProStatus.decide(QueryOutcome.Failure, ProCache(true, now - 7 * DAY - 1), now)
        assertFalse(d.isPro); assertNull(d.cacheToSave)
    }

    @Test fun cacheUsedWhenQueryNotYetDone() {
        val d = ProStatus.decide(QueryOutcome.NotYetQueried, ProCache(true, now - DAY), now)
        assertTrue(d.isPro); assertNull(d.cacheToSave)
    }

    @Test fun futureLastVerifiedTreatedAsWithinGrace() {
        val d = ProStatus.decide(QueryOutcome.Failure, ProCache(true, now + 30 * DAY), now)
        assertTrue(d.isPro)
    }

    @Test fun cachedFreeStaysFreeOnFailure() {
        assertFalse(ProStatus.decide(QueryOutcome.Failure, ProCache(false, now), now).isPro)
    }
}
