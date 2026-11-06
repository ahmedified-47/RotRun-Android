package com.ahmed.rotrun

import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.logic.BillingQuotes
import com.ahmed.rotrun.logic.Products
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingQuotesTest {
    @Test
    fun yearlySaveIsSixtyTwoAgainstTwelveMonthly() {
        val monthly = 12_990_000L
        val yearly = quote(Products.YEARLY, 59_990_000L, 59_990_000L, intro = false)
        assertEquals(62, BillingQuotes.savingsPercent(Products.YEARLY, yearly, monthly))
        assertNull(BillingQuotes.savingsPercent(Products.MONTHLY, quote(Products.MONTHLY, monthly, monthly, false), monthly))
        assertNull(BillingQuotes.savingsPercent(Products.YEARLY, null, monthly))
    }

    @Test
    fun placeholdersMatchTheConfirmedPricesUntilPlayQuotes() {
        assertEquals("US$12.99", BillingQuotes.placeholder(Products.MONTHLY)?.shown)
        assertEquals("US$59.99", BillingQuotes.placeholder(Products.YEARLY)?.shown)
        assertEquals("US$155.99", BillingQuotes.placeholder(Products.YEARLY)?.renewal)
        assertEquals("US$46.99", BillingQuotes.placeholder(Products.YEARLY70)?.shown)
        assertEquals("US$155.99", BillingQuotes.placeholder(Products.YEARLY70)?.renewal)
        assertEquals("US$149.99", BillingQuotes.placeholder(Products.LIFETIME)?.shown)
        assertEquals(62, BillingQuotes.placeholderSavings(Products.YEARLY))
        assertEquals(70, BillingQuotes.placeholderSavings(Products.YEARLY70))
        val live = quote(Products.YEARLY, 10_000_000L, 20_000_000L, intro = true)
        assertEquals(50, BillingQuotes.savingsPercent(Products.YEARLY, live, 12_990_000L))
    }

    @Test
    fun yearly70OffIsIntroAgainstItsOwnRegularPrice() {
        val offer = quote(Products.YEARLY70, 17_990_000L, 59_990_000L, intro = true)
        assertEquals(70, BillingQuotes.savingsPercent(Products.YEARLY70, offer, 12_990_000L))
        assertNull(BillingQuotes.discountPercent(59_990_000L, 59_990_000L))
        assertEquals(0, BillingQuotes.savePercent(1_000_000L, 20_000_000L))
    }

    @Test
    fun offerSelectionUsesExplicitIdOrTheSingleIntroTag() {
        val base = offer("yearly", null, emptyList(), 155_990_000L, "base")
        val firstYear = offer("yearly", "first-year", listOf("intro"), 59_990_000L, "intro-token")
        val other = offer("yearly", "other-intro", listOf("Intro"), 49_990_000L, "other-token")
        val chosen = BillingQuotes.selectSubscription(Products.YEARLY, listOf(other, base, firstYear))
        assertEquals("first-year", chosen?.offerId)
        assertEquals("intro-token", chosen?.offerToken)

        val onlyTag = BillingQuotes.selectSubscription(
            Products.YEARLY,
            listOf(base, offer("yearly", "seasonal", listOf("intro"), 59_990_000L, "tag-token")),
        )
        assertEquals("seasonal", onlyTag?.offerId)

        val ambiguous = BillingQuotes.selectSubscription(Products.YEARLY70, listOf(base, firstYear, other))
        val withoutExplicit = BillingQuotes.selectSubscription(
            Products.MONTHLY,
            listOf(
                offer("monthly", null, emptyList(), 12_990_000L, "month"),
                offer("monthly", "a", listOf("intro"), 9_990_000L, "a"),
                offer("monthly", "b", listOf("intro"), 8_990_000L, "b"),
            ),
        )
        assertEquals("base", ambiguous?.offerToken)
        assertEquals("month", withoutExplicit?.offerToken)
    }

    @Test
    fun freeTrialIsNotAnIntro() {
        val base = offer("yearly", null, emptyList(), 59_990_000L, "base")
        val trial = offer("yearly", "trial", listOf("intro"), 0L, "trial")
        val selected = BillingQuotes.selectSubscription(Products.YEARLY, listOf(trial, base))
        assertEquals("base", selected?.offerToken)
        val priced = BillingQuotes.quoteFor(Products.YEARLY, listOf(base, trial))
        assertEquals(false, priced?.introPresent)
        assertEquals(59_990_000L, priced?.priceMicros)
    }

    @Test
    fun pendingIsNeverAGrant() {
        assertEquals(BillingQuotes.PurchaseDecision.Grant, BillingQuotes.purchaseDecision(purchased = true, pending = false))
        assertEquals(BillingQuotes.PurchaseDecision.Pending, BillingQuotes.purchaseDecision(purchased = false, pending = true))
        assertEquals(BillingQuotes.PurchaseDecision.Ignore, BillingQuotes.purchaseDecision(purchased = false, pending = false))
        assertEquals(BillingQuotes.PurchaseDecision.Grant, BillingQuotes.purchaseDecision(purchased = true, pending = true))
    }

    @Test
    fun yearly70ShowsOnceAndClosingItHidesIt() {
        assertTrue(visible(dismissed = false, premium = false, productId = null, ready = true, unavailable = false, hasQuote = true))
        assertFalse(visible(dismissed = true, premium = false, productId = null, ready = true, unavailable = false, hasQuote = true))
        assertFalse(visible(dismissed = false, premium = true, productId = Products.YEARLY70, ready = true, unavailable = false, hasQuote = true))
        assertFalse(visible(dismissed = false, premium = false, productId = Products.MONTHLY, ready = true, unavailable = false, hasQuote = true))
        assertFalse(visible(dismissed = false, premium = false, productId = Products.YEARLY, ready = true, unavailable = false, hasQuote = true))
        assertFalse(visible(dismissed = false, premium = false, productId = null, ready = false, unavailable = true, hasQuote = false))
        assertFalse(visible(dismissed = false, premium = false, productId = null, ready = true, unavailable = false, hasQuote = false))
        assertTrue(visible(dismissed = false, premium = false, productId = null, ready = false, unavailable = false, hasQuote = false))
    }

    @Test
    fun secondAppKeepsThePreviousOne() {
        val (kept, trimmed) = BillingQuotes.keepingFirst(listOf("a"), listOf("b", "a"), premium = false, paywall = true)
        assertEquals(listOf("a"), kept)
        assertTrue(trimmed)
        val (first, cut) = BillingQuotes.keepingFirst(emptyList(), listOf("b", "a"), premium = false, paywall = true)
        assertEquals(listOf("b"), first)
        assertTrue(cut)
        val (both, noCut) = BillingQuotes.keepingFirst(listOf("a"), listOf("a", "b"), premium = true, paywall = true)
        assertEquals(listOf("a", "b"), both)
        assertFalse(noCut)
    }

    @Test
    fun expiryComesFromTheServerNotAFakeWindow() {
        val previous = 1_700_000_000_000L
        val server = 1_800_000_000_000L
        assertEquals(server, BillingQuotes.premiumUntilMillis(lifetime = false, serverExpiresAt = server, previous = previous))
        assertEquals(previous, BillingQuotes.premiumUntilMillis(lifetime = false, serverExpiresAt = null, previous = previous))
        assertTrue(BillingQuotes.premiumUntilMillis(lifetime = true, serverExpiresAt = null, previous = previous) > previous + 400L * 24 * 60 * 60 * 1000)
        val active = SavedState(premium = true, premiumUntil = 0L)
        val expired = SavedState(premium = true, productId = Products.YEARLY, premiumUntil = 1L)
        assertTrue(active.premiumActive(now = 50_000L))
        assertFalse(expired.premiumActive(now = 50_000L))
    }

    @Test
    fun accountIdIsASha256Hex() {
        val id = "11111111-2222-3333-4444-555555555555"
        val hash = BillingQuotes.obfuscatedAccountId(id)
        assertEquals("666ff6ccaa5b3c07feaa3a95d3a4bd2c46ac9e9abdb09ca9133528d3dc1e8952", hash)
        assertEquals(hash, BillingQuotes.obfuscatedAccountId(id))
        assertFalse(hash.equals(id, ignoreCase = true))
    }

    @Test
    fun catalogIsTheFourLiveProducts() {
        assertEquals(
            listOf(Products.MONTHLY, Products.YEARLY, Products.YEARLY70, Products.LIFETIME),
            Products.all,
        )
        assertFalse(Products.all.any { it.contains("rotrun_premium") })
    }

    @Test
    fun aPlayPurchaseStaysEvenWhenSyncIsDown() {
        assertTrue(BillingQuotes.keepLocalPlayGrant(playPurchased = true))
        assertFalse(BillingQuotes.applyServerEntitlement(playPurchased = true, serverActive = false))
        assertTrue(BillingQuotes.applyServerEntitlement(playPurchased = true, serverActive = true))
        assertFalse(BillingQuotes.keepLocalPlayGrant(playPurchased = false))
        assertTrue(BillingQuotes.shouldAcknowledge(purchased = true, alreadyAcknowledged = false))
        assertFalse(BillingQuotes.shouldAcknowledge(purchased = false, alreadyAcknowledged = false))
        assertFalse(BillingQuotes.shouldAcknowledge(purchased = true, alreadyAcknowledged = true))
        assertFalse(BillingQuotes.mayConsume(Products.LIFETIME))
        assertFalse(BillingQuotes.mayConsume(Products.MONTHLY))
        assertFalse(BillingQuotes.mayConsume(Products.YEARLY))
        assertFalse(BillingQuotes.mayConsume(Products.YEARLY70))
    }

    @Test
    fun upgradesChargeImmediatelyAndDowngradesWait() {
        assertEquals(BillingQuotes.PlanChange.UpgradeImmediate, BillingQuotes.planChange(Products.MONTHLY, Products.YEARLY))
        assertEquals(BillingQuotes.PlanChange.DowngradeDeferred, BillingQuotes.planChange(Products.YEARLY, Products.MONTHLY))
        assertEquals(BillingQuotes.PlanChange.None, BillingQuotes.planChange(Products.YEARLY, Products.YEARLY70))
        assertEquals(BillingQuotes.PlanChange.None, BillingQuotes.planChange(Products.MONTHLY, Products.LIFETIME))
    }

    private fun visible(
        dismissed: Boolean,
        premium: Boolean,
        productId: String?,
        ready: Boolean,
        unavailable: Boolean,
        hasQuote: Boolean,
    ) = BillingQuotes.yearly70Visible(true, premium, dismissed, productId, ready, unavailable, hasQuote)

    private fun quote(id: String, price: Long, regular: Long, intro: Boolean) =
        com.ahmed.rotrun.logic.PlanQuote(
            id = id,
            formattedPrice = "$price",
            priceMicros = price,
            regularFormatted = "$regular",
            regularMicros = regular,
            introPresent = intro,
            renewalFormatted = if (intro) "$regular" else null,
            renewalMicros = if (intro) regular else null,
            offerToken = "token",
        )

    private fun offer(base: String, id: String?, tags: List<String>, micros: Long, token: String) =
        BillingQuotes.Offer(base, id, tags, listOf(BillingQuotes.Phase(micros, "$micros")), token)
}
