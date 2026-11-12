package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SadaqahFlowState {
    data object Idle : SadaqahFlowState
    data object Connecting : SadaqahFlowState
    data class Success(val message: String) : SadaqahFlowState
    data class SetupRequired(val details: String) : SadaqahFlowState
    data class Error(val message: String) : SadaqahFlowState
}

class SadaqahBillingManager(
    context: Context,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val productId = appContext.getString(R.string.sadaqah_product_id).ifBlank { "sadaqah_tip_2myr" }

    private val _state = MutableStateFlow<SadaqahFlowState>(SadaqahFlowState.Idle)
    val state: StateFlow<SadaqahFlowState> = _state.asStateFlow()

    private val _formattedPrice = MutableStateFlow("2 MYR")
    val formattedPrice: StateFlow<String> = _formattedPrice.asStateFlow()

    private var cachedProductDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                if (list.isEmpty()) {
                    _state.value = SadaqahFlowState.Idle
                } else {
                    list.forEach { purchase -> handlePurchase(purchase, emitSuccess = true) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = SadaqahFlowState.Idle
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch { consumeExistingPurchases() }
            }
            else -> {
                _state.value = SadaqahFlowState.Error(
                    billingResult.debugMessage.ifBlank {
                        "Google Play Billing returned code ${billingResult.responseCode}."
                    }
                )
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun warmUp() {
        if (billingClient.isReady) {
            scope.launch {
                consumeExistingPurchases()
                fetchProductDetails()
            }
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        consumeExistingPurchases()
                        fetchProductDetails()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto-reconnection enabled.
            }
        })
    }

    fun startSadaqahTip(activity: Activity?) {
        if (activity == null) {
            _state.value = SadaqahFlowState.Error("Activity unavailable to launch Google Play checkout.")
            return
        }

        _state.value = SadaqahFlowState.Connecting

        val cached = cachedProductDetails
        if (billingClient.isReady && cached != null) {
            launchFlow(activity, cached)
            return
        }

        if (billingClient.isReady) {
            scope.launch { queryAndLaunch(activity) }
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        consumeExistingPurchases()
                        queryAndLaunch(activity)
                    }
                } else {
                    Log.w(
                        TAG,
                        "Play Billing setup unavailable (${billingResult.responseCode}): ${billingResult.debugMessage}"
                    )
                    _state.value = SadaqahFlowState.SetupRequired(
                        "Google Play Billing is not active yet (${billingResult.debugMessage.ifBlank { "code ${billingResult.responseCode}" }}).\n\n" +
                            "Once uploaded to Google Play Console (Internal/Closed Testing or Production) with active in-app product \"$productId\" priced at MYR 2.00, tapping this button opens Google Play checkout."
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                if (_state.value is SadaqahFlowState.Connecting) {
                    _state.value = SadaqahFlowState.Error("Disconnected from Google Play Billing service.")
                }
            }
        })
    }

    private suspend fun fetchProductDetails(): ProductDetails? {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        val result = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params)
        }

        val details = result.productDetailsList.orEmpty().firstOrNull()
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK && details != null) {
            cachedProductDetails = details
            val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
                ?: details.oneTimePurchaseOfferDetails
            val price = offer?.formattedPrice
            if (!price.isNullOrBlank()) {
                _formattedPrice.value = price
            }
        }
        return details
    }

    private suspend fun queryAndLaunch(activity: Activity) {
        val details = fetchProductDetails()
        if (details != null) {
            launchFlow(activity, details)
        } else {
            _state.value = SadaqahFlowState.SetupRequired(
                "In-app product \"$productId\" (2 MYR) was not found on Google Play yet.\n\n" +
                    "To activate it: upload the release AAB to Google Play Console, create an In-App Product with ID \"$productId\" priced at MYR 2.00, and mark it Active."
            )
        }
    }

    private fun launchFlow(activity: Activity, productDetails: ProductDetails) {
        val offer = productDetails.oneTimePurchaseOfferDetailsList?.firstOrNull()
            ?: productDetails.oneTimePurchaseOfferDetails
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
        if (!offer?.offerToken.isNullOrBlank()) {
            productParamsBuilder.setOfferToken(offer!!.offerToken!!)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        val launchResult = billingClient.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = SadaqahFlowState.Error(
                launchResult.debugMessage.ifBlank { "Unable to open Google Play checkout." }
            )
        }
    }

    private suspend fun consumeExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val purchasesResult = withContext(Dispatchers.IO) {
            billingClient.queryPurchasesAsync(params)
        }
        if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            purchasesResult.purchasesList
                .filter { it.products.contains(productId) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { handlePurchase(it, emitSuccess = false) }
        }
    }

    private fun handlePurchase(purchase: Purchase, emitSuccess: Boolean) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            scope.launch {
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                withContext(Dispatchers.IO) {
                    billingClient.consumePurchase(consumeParams)
                }
                if (emitSuccess) {
                    _state.value = SadaqahFlowState.Success(
                        "JazakAllahu Khayran! Your ${_formattedPrice.value} Sadaqah was received via Google Play."
                    )
                }
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING && emitSuccess) {
            _state.value = SadaqahFlowState.Success(
                "Payment is pending confirmation on Google Play. JazakAllahu Khayran!"
            )
        }
    }

    fun release() {
        runCatching { billingClient.endConnection() }
    }

    private companion object {
        const val TAG = "SadaqahBilling"
    }
}
