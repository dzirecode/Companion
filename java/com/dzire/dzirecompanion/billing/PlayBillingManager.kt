package com.dzire.dzirecompanion.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Client-side Play Billing integration.
 * Product ID is configured in Play Console as: dzire_premium
 *
 * Production entitlement should be verified server-side using the purchase token.
 */
class PlayBillingManager(context: Context) :
    BillingClient.ProductDetailsResponseListener,
    BillingClient.PurchasesUpdatedListener {

    companion object { const val PREMIUM_PRODUCT_ID = "dzire_premium" }

    private val _state = MutableStateFlow(SubscriptionState(loading = true))
    val state: StateFlow<SubscriptionState> = _state

    private var productDetails: ProductDetails? = null
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init { connect() }

    private fun connect() {
        billingClient.startConnection(object : BillingClient.BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingClient.BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryExistingPurchases()
                } else {
                    _state.value = SubscriptionState(error = result.debugMessage)
                }
            }
            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(loading = false, error = "Google Play Billing is temporarily unavailable.")
            }
        })
    }

    private fun queryProduct() {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(ProductType.SUBS)
                .build()
        )
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(), this
        )
    }

    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                applyPurchases(purchases)
            }
        }
    }

    override fun onProductDetailsResponse(
        result: BillingClient.BillingResult,
        productDetailsResult: QueryProductDetailsResult
    ) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = SubscriptionState(error = result.debugMessage)
            return
        }
        productDetails = productDetailsResult.productDetailsList.firstOrNull()
        val offer = productDetails?.subscriptionOfferDetails?.firstOrNull()
        val price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        _state.value = _state.value.copy(loading = false, price = price, error = null)
    }

    override fun onPurchasesUpdated(result: BillingClient.BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            applyPurchases(purchases)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            _state.value = _state.value.copy(error = result.debugMessage)
        }
    }

    private fun applyPurchases(purchases: List<Purchase>) {
        val active = purchases.firstOrNull { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.contains(PREMIUM_PRODUCT_ID)
        }
        if (active != null && !active.isAcknowledged) {
            val params = com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(active.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { }
        }
        _state.value = _state.value.copy(isPremium = active != null, productId = active?.products?.firstOrNull())
    }

    fun launchPremium(activity: Activity): BillingClient.BillingResult? {
        val details = productDetails ?: return null
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return null
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        return billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
        )
    }
}
