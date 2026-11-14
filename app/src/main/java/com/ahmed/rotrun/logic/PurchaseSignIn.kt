package com.ahmed.rotrun.logic

/**
 * Play Billing needs a Supabase user id so the purchase can carry that account.
 * No id starts Google sign-in first. A finished sign-in with an id continues into Play.
 * A cancel, a failure, or a missing id stays on the paywall and does not open Play.
 */
object PurchaseSignIn {
    enum class Next { StartSignIn, LaunchBilling, StayOnPaywall }

    fun beforeBilling(userId: String?): Next =
        if (userId.isNullOrBlank()) Next.StartSignIn else Next.LaunchBilling

    fun afterSignIn(userId: String?, error: String?): Next = when {
        !error.isNullOrBlank() -> Next.StayOnPaywall
        userId.isNullOrBlank() -> Next.StayOnPaywall
        else -> Next.LaunchBilling
    }
}
