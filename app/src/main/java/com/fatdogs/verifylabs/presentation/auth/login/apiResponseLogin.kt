package com.fatdogs.verifylabs.presentation.auth.login

import com.google.gson.annotations.SerializedName

data class apiResponseLogin(
    @SerializedName("api_key")
    val apiKey: String,
    @SerializedName("expiry")
    val expiry: String,
    @SerializedName("credits_monthly")
    val creditsMonthly: Int,
    @SerializedName("credits")
    val credits: Int
)
