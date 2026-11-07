package com.ahmed.rotrun.logic

import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Play Billing decisions that do not need a BillingClient.
 * Product IDs match iOS. Offer IDs match the Play catalog Ahmed creates.
 */
object BillingQuotes {
    const val INTRO_TAG = "intro"
    const val YEARLY_INTRO_OFFER = "first-year"
    const val YEARLY70_INTRO_OFFER = "first-year-70"
    const val LIFETIME_OPTION = "buy"
    const val PENDING_MESSAGE = "This purchase is pending approval."
    const val SIGN_IN_MESSAGE = "Sign in with Google to get Premium."
    const val UNAVAILABLE_MESSAGE = "Subscriptions aren't available yet. Try again later."
    const val RESTORED_MESSAGE = "Purchases restored."
    const val NOTHING_TO_RESTORE = "No active RotRun Premium subscription found for this Google account."
    const val LIFETIME_CANCEL_SUB = "Lifetime is on. Cancel your subscription in Google Play so it doesn't renew."
    const val LEGAL_FOOTER = "Payment is charged to your Google Play account at confirmation. Subscriptions auto-renew unless you cancel before the end of the current period. Manage or cancel in Google Play → Payments & subscriptions. Lifetime is a one-time purchase and does not renew."

    data class Phase(val priceMicros: Long, val formattedPrice: String)

    data class Offer(
        val basePlanId: String,
        val offerId: String?,
        val tags: List<String>,
        val phases: List<Phase>,
        val offerToken: String,
    )

    enum class PurchaseDecision { Grant, Pending, Ignore }

    enum class PlanChange { None, UpgradeImmediate, DowngradeDeferred }

    fun introOfferId(productId: String): String? = when (productId) {
        Products.YEARLY -> YEARLY_INTRO_OFFER
        Products.YEARLY70 -> YEARLY70_INTRO_OFFER
        else -> null
    }

    fun basePlanId(productId: String): String? = when (productId) {
        Products.MONTHLY -> "monthly"
        Products.YEARLY, Products.YEARLY70 -> "yearly"
        Products.LIFETIME -> LIFETIME_OPTION
        else -> null
    }

    /** Base plan is the row with no offer id. An intro is the explicit offer id, or the only paid `intro` tag. */
    fun selectSubscription(productId: String, offers: List<Offer>): Offer? {
        val preferredBase = basePlanId(productId)
        val bases = offers.filter { it.offerId == null }
        val base = bases.singleOrNull { preferredBase == null || it.basePlanId == preferredBase }
            ?: bases.singleOrNull()
            ?: return null
        val intros = offers.filter { offer ->
            offer.offerId != null &&
                offer.basePlanId == base.basePlanId &&
                offer.tags.any { it.equals(INTRO_TAG, ignoreCase = true) } &&
                (offer.phases.firstOrNull()?.priceMicros ?: 0L) > 0L
        }
        val expected = introOfferId(productId)
        val intro = intros.singleOrNull { it.offerId == expected } ?: intros.singleOrNull()
        return intro ?: base
    }

    fun quoteFor(productId: String, offers: List<Offer>): PlanQuote? {
        val preferredBase = basePlanId(productId)
        val bases = offers.filter { it.offerId == null }
        val base = bases.singleOrNull { preferredBase == null || it.basePlanId == preferredBase }
            ?: bases.singleOrNull()
            ?: return null
        val chosen = selectSubscription(productId, offers) ?: return null
        val basePhase = base.phases.lastOrNull() ?: return null
        val shown = chosen.phases.firstOrNull() ?: return null
        val intro = chosen.offerId != null
        return PlanQuote(
            id = productId,
            formattedPrice = shown.formattedPrice,
            priceMicros = shown.priceMicros,
            regularFormatted = basePhase.formattedPrice,
            regularMicros = basePhase.priceMicros,
            introPresent = intro,
            renewalFormatted = if (intro) basePhase.formattedPrice else null,
            renewalMicros = if (intro) basePhase.priceMicros else null,
            offerToken = chosen.offerToken,
        )
    }

    fun lifetimeQuote(optionId: String?, formattedPrice: String, priceMicros: Long, offerToken: String): PlanQuote? {
        if (optionId != null && optionId != LIFETIME_OPTION && optionId != Products.LIFETIME) return null
        if (formattedPrice.isBlank() || priceMicros <= 0L) return null
        return PlanQuote(
            id = Products.LIFETIME,
            formattedPrice = formattedPrice,
            priceMicros = priceMicros,
            regularFormatted = formattedPrice,
            regularMicros = priceMicros,
            introPresent = false,
            renewalFormatted = null,
            renewalMicros = null,
            offerToken = offerToken,
        )
    }

    /**
     * Shown only until Play returns a formatted price. Confirmed 4 Nov 2026.
     * yearly70's Australian first year is A$69.99; this placeholder stays in US dollars.
     */
    data class PricePlaceholder(
        val shown: String,
        val renewal: String?,
        val shownMicros: Long,
        val regularMicros: Long,
        val intro: Boolean,
    )

    fun placeholder(productId: String): PricePlaceholder? = when (productId) {
        Products.MONTHLY -> PricePlaceholder("US$12.99", null, 12_990_000L, 12_990_000L, false)
        Products.YEARLY -> PricePlaceholder("US$59.99", "US$155.99", 59_990_000L, 155_990_000L, true)
        Products.YEARLY70 -> PricePlaceholder("US$46.99", "US$155.99", 46_990_000L, 155_990_000L, true)
        Products.LIFETIME -> PricePlaceholder("US$149.99", null, 149_990_000L, 149_990_000L, false)
        else -> null
    }

    /** Badge maths for a missing quote. A real [PlanQuote] never uses these micros. */
    fun placeholderSavings(productId: String): Int? {
        val place = placeholder(productId) ?: return null
        return savingsPercent(
            productId,
            PlanQuote(
                id = productId,
                formattedPrice = place.shown,
                priceMicros = place.shownMicros,
                regularFormatted = place.renewal ?: place.shown,
                regularMicros = place.regularMicros,
                introPresent = place.intro,
                renewalFormatted = place.renewal,
                renewalMicros = if (place.intro) place.regularMicros else null,
                offerToken = "",
            ),
            placeholder(Products.MONTHLY)?.regularMicros,
        )
    }

    /**
     * Intro versus that product's regular price. Otherwise yearly or yearly70 versus 12× monthly.
     * Hidden when the result is missing or not a real saving.
     */
    fun savingsPercent(productId: String, quote: PlanQuote?, monthlyRegularMicros: Long?): Int? {
        if (quote == null) return null
        if (quote.introPresent) return discountPercent(quote.regularMicros, quote.priceMicros)
        if (productId != Products.YEARLY && productId != Products.YEARLY70) return null
        val monthly = monthlyRegularMicros ?: return null
        return savePercent(monthly, quote.priceMicros).takeIf { it > 0 }
    }

    fun discountPercent(regularMicros: Long, discountedMicros: Long): Int? {
        if (regularMicros <= 0L || discountedMicros <= 0L || discountedMicros >= regularMicros) return null
        val percent = ((regularMicros - discountedMicros) * 100.0 / regularMicros).roundToInt()
        return percent.takeIf { it > 0 }
    }

    fun savePercent(monthlyMicros: Long, yearlyMicros: Long): Int {
        val annual = monthlyMicros * 12
        if (annual <= 0L || yearlyMicros <= 0L) return 0
        return ((annual - yearlyMicros) * 100.0 / annual).roundToInt().coerceAtLeast(0)
    }

    fun purchaseDecision(purchased: Boolean, pending: Boolean): PurchaseDecision = when {
        purchased -> PurchaseDecision.Grant
        pending -> PurchaseDecision.Pending
        else -> PurchaseDecision.Ignore
    }

    /** A Play PURCHASED row stays Pro even when sync-play-entitlements is down or returns nothing. */
    fun keepLocalPlayGrant(playPurchased: Boolean): Boolean = playPurchased

    /** Apply a server expiry only when that same purchase is still active. Never let an empty sync clear it. */
    fun applyServerEntitlement(playPurchased: Boolean, serverActive: Boolean): Boolean = playPurchased && serverActive

    /** Subscriptions and lifetime are not consumable. Lifetime must never be consumed. */
    fun mayConsume(productId: String): Boolean = false

    /** Acknowledge every PURCHASED row within Play's 3-day window, including when the server sync failed. */
    fun shouldAcknowledge(purchased: Boolean, alreadyAcknowledged: Boolean): Boolean = purchased && !alreadyAcknowledged

    fun planChange(fromProductId: String?, toProductId: String): PlanChange {
        if (fromProductId.isNullOrBlank() || fromProductId == toProductId) return PlanChange.None
        if (toProductId == Products.LIFETIME || fromProductId == Products.LIFETIME) return PlanChange.None
        if ((fromProductId == Products.YEARLY && toProductId == Products.YEARLY70) ||
            (fromProductId == Products.YEARLY70 && toProductId == Products.YEARLY)
        ) {
            return PlanChange.None
        }
        val from = rank(fromProductId) ?: return PlanChange.None
        val to = rank(toProductId) ?: return PlanChange.None
        return if (to < from) PlanChange.UpgradeImmediate else PlanChange.DowngradeDeferred
    }

    fun yearly70Visible(
        paywall: Boolean,
        premium: Boolean,
        dismissed: Boolean,
        productId: String?,
        catalogReady: Boolean,
        catalogUnavailable: Boolean,
        hasQuote: Boolean,
    ): Boolean {
        if (!paywall || premium || dismissed) return false
        if (productId == Products.MONTHLY || productId == Products.YEARLY) return false
        if (catalogUnavailable) return false
        if (catalogReady && !hasQuote) return false
        return true
    }

    /** Keep the previous app when it is still chosen. Otherwise keep the first. A trim opens the paywall. */
    fun keepingFirst(previous: List<String>, proposed: List<String>, premium: Boolean, paywall: Boolean): Pair<List<String>, Boolean> {
        val unique = proposed.filter { it.isNotBlank() }.distinct()
        if (!paywall || premium || unique.size <= BlockRules.FREE_APP_LIMIT) return unique to false
        val kept = previous.firstOrNull { it in unique } ?: unique.first()
        return listOf(kept) to true
    }

    /** SHA-256 hex. Play's obfuscated account id is 64 characters, which is this digest. */
    fun obfuscatedAccountId(userId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** A missing server date does not invent an expiry. Lifetime has no renewal. */
    fun premiumUntilMillis(lifetime: Boolean, serverExpiresAt: Long?, previous: Long): Long = when {
        lifetime -> Long.MAX_VALUE / 4
        serverExpiresAt != null -> serverExpiresAt
        else -> previous
    }

    private fun rank(productId: String): Int? = when (productId) {
        Products.YEARLY, Products.YEARLY70 -> 1
        Products.MONTHLY -> 2
        else -> null
    }
}
