package com.fatdogs.verifylabs.presentation.plan

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PlanResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: String,
    @SerializedName("credits") val credits: Int,
    @SerializedName("recurrence") val recurrence: Int,
    @SerializedName("credits_price") val creditsPrice: String,
    @SerializedName("credits_bundle") val creditsBundle: Int,
    @SerializedName("shopify_credits") val shopifyCredits: String,
    @SerializedName("shopify_id") val shopifyId: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
) : Serializable
