package com.dzire.dzirecompanion.billing

data class SubscriptionState(
    val isPremium: Boolean = false,
    val productId: String? = null,
    val price: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)
