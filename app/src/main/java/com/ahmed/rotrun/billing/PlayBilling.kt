package com.ahmed.rotrun.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ahmed.rotrun.logic.BillingQuotes
import com.ahmed.rotrun.logic.CatalogStatus
import com.ahmed.rotrun.logic.PaywallGate
import com.ahmed.rotrun.logic.PlanQuote
import com.ahmed.rotrun.logic.PlayCatalog
import com.ahmed.rotrun.logic.Products
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
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayPurchaseUpload(val productId: String, val purchaseToken: String, val kind: String)

data class ServerEntitlement(
    val productId: String,
    val expiresAtMillis: Long?,
    val lifetime: Boolean,
    val active: Boolean = true,
)

class PlayBilling(
    context: Context,
    private val accountId: () -> String?,
    private val onEntitlement: (productId: String?, lifetime: Boolean, active: Boolean, expiresAtMillis: Long?) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onPurchased: () -> Unit,
    private val sync: (List<PlayPurchaseUpload>) -> List<ServerEntitlement>?,
) : PurchasesUpdatedListener {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val details = HashMap<String, ProductDetails>()
    private var ownedSubscriptionToken: String? = null
    private var ownedSubscriptionProduct: String? = null
    private val client = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (!PaywallGate.shows) {
            PlayCatalog.status.value = CatalogStatus.Unavailable
            return
        }
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else if (result.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ||
                    result.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
                ) {
                    PlayCatalog.status.value = CatalogStatus.Unavailable
                } else {
                    PlayCatalog.status.value = CatalogStatus.Unavailable
                    Log.w(TAG, "billing setup ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun buy(activity: Activity, planId: String) {
        if (!PaywallGate.shows) return
        val quote = PlayCatalog.quote(planId)
        val product = details[planId]
        if (quote == null || product == null || quote.offerToken.isBlank()) {
            onMessage(BillingQuotes.UNAVAILABLE_MESSAGE)
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(quote.offerToken)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
        val userId = accountId()?.takeIf { it.isNotBlank() }
        if (userId != null) {
            flow.setObfuscatedAccountId(BillingQuotes.obfuscatedAccountId(userId))
        }
        val from = ownedSubscriptionProduct
        val token = ownedSubscriptionToken
        when (BillingQuotes.planChange(from, planId)) {
            BillingQuotes.PlanChange.UpgradeImmediate -> if (!token.isNullOrBlank()) {
                flow.setSubscriptionUpdateParams(update(token, BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE))
            }
            BillingQuotes.PlanChange.DowngradeDeferred -> if (!token.isNullOrBlank()) {
                flow.setSubscriptionUpdateParams(update(token, BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED))
            }
            BillingQuotes.PlanChange.None -> Unit
        }
        PlayCatalog.purchasing.value = true
        val result = client.launchBillingFlow(activity, flow.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            PlayCatalog.purchasing.value = false
            Log.w(TAG, "launch ${result.responseCode} ${result.debugMessage}")
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
        PlayCatalog.purchasing.value = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> return
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryOwned(announce = true)
                return
            }
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), fromPurchase = true)
            else -> {
                Log.w(TAG, "purchase ${result.responseCode} ${result.debugMessage}")
                onMessage("Google Play couldn’t finish that purchase.")
            }
        }
    }

    private fun refresh() {
        queryProducts()
        queryOwned(announce = false)
    }

    private fun queryProducts() {
        val subs = Products.subscriptions.map { id ->
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
        ) { result, detailsResult ->
            val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsResult.productDetailsList
            } else {
                emptyList()
            }
            client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(lifetime)).build(),
            ) { inAppResult, inAppResultDetails ->
                val all = found.toMutableList()
                if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    all += inAppResultDetails.productDetailsList
                }
                if (result.responseCode != BillingClient.BillingResponseCode.OK &&
                    inAppResult.responseCode != BillingClient.BillingResponseCode.OK
                ) {
                    PlayCatalog.status.value = CatalogStatus.Unavailable
                    return@queryProductDetailsAsync
                }
                publish(all)
            }
        }
    }

    private fun publish(products: List<ProductDetails>) {
        details.clear()
        val quotes = LinkedHashMap<String, PlanQuote>()
        products.forEach { product ->
            if (product.productId == Products.LIFETIME) {
                val offer = lifetimeOffer(product) ?: return@forEach
                val quote = BillingQuotes.lifetimeQuote(offer.first, offer.second, offer.third, offer.fourth) ?: return@forEach
                details[Products.LIFETIME] = product
                quotes[Products.LIFETIME] = quote
                return@forEach
            }
            if (product.productId !in Products.subscriptions) return@forEach
            val offers = product.subscriptionOfferDetails.orEmpty().map { offer ->
                BillingQuotes.Offer(
                    basePlanId = offer.basePlanId,
                    offerId = offer.offerId,
                    tags = offer.offerTags,
                    phases = offer.pricingPhases.pricingPhaseList.map { phase ->
                        BillingQuotes.Phase(phase.priceAmountMicros, phase.formattedPrice)
                    },
                    offerToken = offer.offerToken,
                )
            }
            val quote = BillingQuotes.quoteFor(product.productId, offers) ?: return@forEach
            details[product.productId] = product
            quotes[product.productId] = quote
        }
        PlayCatalog.plans.value = quotes
        PlayCatalog.status.value = if (quotes.isEmpty()) CatalogStatus.Unavailable else CatalogStatus.Ready
    }

    private fun lifetimeOffer(product: ProductDetails): Quadruple? {
        val listed = product.oneTimePurchaseOfferDetailsList.orEmpty()
        val chosen = listed.singleOrNull { it.purchaseOptionId == BillingQuotes.LIFETIME_OPTION }
            ?: listed.singleOrNull()
            ?: product.oneTimePurchaseOfferDetails
            ?: return null
        return Quadruple(chosen.purchaseOptionId, chosen.formattedPrice, chosen.priceAmountMicros, chosen.offerToken.orEmpty())
    }

    private fun queryOwned(announce: Boolean) {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { subsResult, subs ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            ) { inAppResult, inApp ->
                if (subsResult.responseCode != BillingClient.BillingResponseCode.OK ||
                    inAppResult.responseCode != BillingClient.BillingResponseCode.OK
                ) {
                    Log.w(TAG, "query ${subsResult.responseCode} ${inAppResult.responseCode}")
                    return@queryPurchasesAsync
                }
                handle(subs + inApp, fromPurchase = false, announce = announce)
            }
        }
    }

    private fun handle(purchases: List<Purchase>, fromPurchase: Boolean, announce: Boolean = false) {
        val known = purchases.filter { purchase -> purchase.products.any { it in Products.all } }
        val pending = known.filter { it.purchaseState == Purchase.PurchaseState.PENDING }
        val purchased = known.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (pending.isNotEmpty()) onMessage(BillingQuotes.PENDING_MESSAGE)
        val lifetime = purchased.firstOrNull { Products.LIFETIME in it.products }
        val subscription = purchased.firstOrNull { purchase -> purchase.products.any { it in Products.subscriptions } }
        ownedSubscriptionToken = subscription?.purchaseToken
        ownedSubscriptionProduct = subscription?.products?.firstOrNull { it in Products.subscriptions }
        val active = lifetime ?: subscription
        val productId = when {
            lifetime != null -> Products.LIFETIME
            subscription != null -> subscription.products.firstOrNull { it in Products.subscriptions }
            else -> null
        }
        val isLifetime = lifetime != null
        if (!BillingQuotes.keepLocalPlayGrant(active != null)) {
            onEntitlement(null, false, false, null)
        } else {
            onEntitlement(productId, isLifetime, true, null)
        }
        val uploads = purchased.mapNotNull { purchase ->
            val id = purchase.products.firstOrNull { it in Products.all } ?: return@mapNotNull null
            if (BillingQuotes.mayConsume(id)) {
                Log.e(TAG, "refusing to consume $id")
            }
            PlayPurchaseUpload(
                productId = id,
                purchaseToken = purchase.purchaseToken,
                kind = if (id == Products.LIFETIME) "inapp" else "subs",
            )
        }.take(8)
        scope.launch {
            val signedIn = !accountId().isNullOrBlank()
            val rows = if (!signedIn || uploads.isEmpty()) {
                null
            } else {
                withContext(Dispatchers.IO) { runCatching { sync(uploads) }.getOrNull() }
            }
            if (rows != null && BillingQuotes.keepLocalPlayGrant(active != null)) {
                val row = rows.firstOrNull { it.productId == productId && it.active }
                    ?: rows.firstOrNull { it.active }
                if (row != null && BillingQuotes.applyServerEntitlement(true, row.active)) {
                    onEntitlement(row.productId, row.lifetime || row.productId == Products.LIFETIME, true, row.expiresAtMillis)
                }
            }
            purchased.filter {
                BillingQuotes.shouldAcknowledge(
                    purchased = it.purchaseState == Purchase.PurchaseState.PURCHASED,
                    alreadyAcknowledged = it.isAcknowledged,
                )
            }.forEach { acknowledge(it) }
            if (fromPurchase && active != null) {
                if (isLifetime && subscription != null) onMessage(BillingQuotes.LIFETIME_CANCEL_SUB)
                onPurchased()
            }
            if (announce) {
                onMessage(if (purchased.isEmpty()) BillingQuotes.NOTHING_TO_RESTORE else BillingQuotes.RESTORED_MESSAGE)
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "ack ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun update(token: String, mode: Int): BillingFlowParams.SubscriptionUpdateParams =
        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
            .setOldPurchaseToken(token)
            .setSubscriptionReplacementMode(mode)
            .build()

    private data class Quadruple(val first: String?, val second: String, val third: Long, val fourth: String)

    private companion object {
        const val TAG = "PlayBilling"
    }
}
