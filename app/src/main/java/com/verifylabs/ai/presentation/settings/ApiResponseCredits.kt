package com.verifylabs.ai.presentation.settings

import com.google.gson.annotations.SerializedName


data class ApiResponseCredits(
    @SerializedName("credits")
    val credits: Int? = 0,

    @SerializedName("credits_monthly")
    val creditsMonthly: Int? = 0
)
