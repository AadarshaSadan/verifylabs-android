package com.verifylabs.ai.presentation.plan

import com.revenuecat.purchases.Package

data class CreditPackage(
    val imageRes: Int,
    val credits: Int,
    val priceUsd: Double,
    val name: String,
    val description: String,
    val rcPackage: Package,
    val isSubscription: Boolean = false,
    val isBestValue: Boolean = false
) {
    val formattedPrice: String
        get() = rcPackage.product.price.formatted

    val buttonText: String
        get() = if (isSubscription) "Subscribe" else "Buy"
}