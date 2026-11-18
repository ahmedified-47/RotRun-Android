package com.ahmed.rotrun

import com.ahmed.rotrun.logic.PurchaseSignIn
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseSignInTest {
    @Test
    fun notSignedInStartsSignInThenPurchase() {
        assertEquals(PurchaseSignIn.Next.StartSignIn, PurchaseSignIn.beforeBilling(null))
        assertEquals(PurchaseSignIn.Next.StartSignIn, PurchaseSignIn.beforeBilling("  "))
        assertEquals(PurchaseSignIn.Next.LaunchBilling, PurchaseSignIn.beforeBilling("user-id"))
        assertEquals(PurchaseSignIn.Next.LaunchBilling, PurchaseSignIn.afterSignIn("user-id", null))
        assertEquals(PurchaseSignIn.Next.LaunchBilling, PurchaseSignIn.afterSignIn("user-id", "  "))
        assertEquals(PurchaseSignIn.Next.StayOnPaywall, PurchaseSignIn.afterSignIn(null, "Google sign-in was cancelled."))
        assertEquals(PurchaseSignIn.Next.StayOnPaywall, PurchaseSignIn.afterSignIn("user-id", "Sign-in failed"))
        assertEquals(PurchaseSignIn.Next.StayOnPaywall, PurchaseSignIn.afterSignIn(null, null))
        assertEquals(PurchaseSignIn.Next.StayOnPaywall, PurchaseSignIn.afterSignIn("", null))
    }
}
