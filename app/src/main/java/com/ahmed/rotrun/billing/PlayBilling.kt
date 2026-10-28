package com.ahmed.rotrun.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.ahmed.rotrun.logic.CatalogStatus
import com.ahmed.rotrun.logic.PaywallGate
import com.ahmed.rotrun.logic.PlanQuote
import com.ahmed.rotrun.logic.PlayCatalog
import com.ahmed.rotrun.logic.Products

class PlayBilling(
    context: Context,
    private val onEntitlement: (productId: String?, lifetime: Boolean, active: Boolean) -> Unit,
    private val onMessage: (String) -> Unit,
) : PurchasesUpdatedListener {
    private val details = HashMap<String, ProductDetails>()
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    fun start() {
        if (!PaywallGate.shows) {
            PlayCatalog.status.value = CatalogStatus.Unavailable
            return
        }
        if (client.isReady) {
            queryProducts()
            queryOwned()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryOwned()
                } else {
                    PlayCatalog.status.value = CatalogStatus.Unavailable
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun buy(activity: Activity, planId: String) {
        if (!PaywallGate.shows) return
        val quote = PlayCatalog.quote(planId)
        val product = details[planId]
        if (quote == null || product == null) {
            onMessage("Google Play doesn’t have this plan on this account yet.")
            return
        }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .apply { if (quote.offerToken.isNotEmpty()) setOfferToken(quote.offerToken) }
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .build()
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage("Google Play couldn’t open checkout.")
        }
    }

    fun restore() {
        if (!client.isReady) {
            start()
            onMessage("Checking Google Play…")
            return
        }
        queryOwned(announce = true)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) return
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage("Google Play couldn’t finish that purchase.")
            return
        }
        purchases.orEmpty().forEach { acknowledge(it) }
        queryOwned()
    }

    private fun queryProducts() {
        val subs = listOf(Products.MONTHLY, Products.YEARLY, Products.YEARLY70, Products.SUBSCRIPTION).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val lifetime = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(Products.LIFETIME)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(subs).build(),
        ) { result, list ->
            val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) list else emptyList()
            client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(lifetime)).build(),
            ) { inAppResult, inApp ->
                val all = found.toMutableList()
                if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) all += inApp
                publish(all)
            }
        }
    }

    private fun publish(products: List<ProductDetails>) {
        details.clear()
        val quotes = LinkedHashMap<String, PlanQuote>()
        products.forEach { product ->
            if (product.productType == BillingClient.ProductType.INAPP && product.productId == Products.LIFETIME) {
                val offer = product.oneTimePurchaseOfferDetails ?: return@forEach
                details[Products.LIFETIME] = product
                quotes[Products.LIFETIME] = PlanQuote(
                    id = Products.LIFETIME,
                    formattedPrice = offer.formattedPrice,
                    priceMicros = offer.priceAmountMicros,
                    renewalFormatted = null,
                    renewalMicros = null,
                    offerToken = "",
                )
                return@forEach
            }
            val offers = product.subscriptionOfferDetails.orEmpty()
            if (product.productId == Products.SUBSCRIPTION) {
                mapBasePlans(product, offers, quotes)
            } else {
                val offer = offers.firstOrNull() ?: return@forEach
                details[product.productId] = product
                quotes[product.productId] = quoteFor(product.productId, offer)
            }
        }
        val tagged = products.firstOrNull { it.productId == Products.SUBSCRIPTION }
            ?.subscriptionOfferDetails
            ?.firstOrNull { offer ->
                offer.offerTags.any { it.equals("yearly70", true) } || offer.offerId?.contains("70") == true
            }
        if (tagged != null && quotes[Products.YEARLY70] == null) {
            details[Products.YEARLY70] = products.first { it.productId == Products.SUBSCRIPTION }
            quotes[Products.YEARLY70] = quoteFor(Products.YEARLY70, tagged)
        }
        PlayCatalog.plans.value = quotes
        PlayCatalog.status.value = if (quotes.isEmpty()) CatalogStatus.Unavailable else CatalogStatus.Ready
    }

    private fun mapBasePlans(
        product: ProductDetails,
        offers: List<ProductDetails.SubscriptionOfferDetails>,
        quotes: MutableMap<String, PlanQuote>,
    ) {
        fun pick(base: String, planId: String) {
            val match = offers.filter { it.basePlanId == base }
            val regular = match.filterNot { offer ->
                offer.offerTags.any { it.contains("70", true) } || offer.offerId?.contains("70") == true
            }
            val offer = regular.firstOrNull { it.offerId != null } ?: regular.firstOrNull() ?: return
            details[planId] = product
            quotes[planId] = quoteFor(planId, offer)
        }
        pick("monthly", Products.MONTHLY)
        pick("yearly", Products.YEARLY)
    }

    private fun quoteFor(id: String, offer: ProductDetails.SubscriptionOfferDetails): PlanQuote {
        val phases = offer.pricingPhases.pricingPhaseList
        val first = phases.first()
        val last = phases.last()
        val renewal = if (phases.size > 1 && last.priceAmountMicros != first.priceAmountMicros) last else null
        return PlanQuote(
            id = id,
            formattedPrice = first.formattedPrice,
            priceMicros = first.priceAmountMicros,
            renewalFormatted = renewal?.formattedPrice,
            renewalMicros = renewal?.priceAmountMicros,
            offerToken = offer.offerToken,
        )
    }

    private fun queryOwned(announce: Boolean = false) {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { _, subs ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            ) { _, inApp ->
                val owned = (subs + inApp).filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                owned.forEach { acknowledge(it) }
                val lifetime = owned.firstOrNull { Products.LIFETIME in it.products }
                val sub = owned.firstOrNull { purchase ->
                    purchase.products.any {
                        it == Products.MONTHLY || it == Products.YEARLY || it == Products.YEARLY70 || it == Products.SUBSCRIPTION
                    }
                }
                when {
                    lifetime != null -> onEntitlement(Products.LIFETIME, true, true)
                    sub != null -> onEntitlement(sub.products.firstOrNull(), false, true)
                    else -> onEntitlement(null, false, false)
                }
                if (announce) {
                    onMessage(
                        if (owned.isEmpty()) "No active RotRun Premium purchase found for this account."
                        else "Purchases restored.",
                    )
                }
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client.acknowledgePurchase(params) { }
    }
}
