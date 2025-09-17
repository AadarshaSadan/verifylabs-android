package com.fatdogs.verifylabs.presentation.media

import com.google.gson.annotations.SerializedName


data class VerificationResponse(
    @SerializedName("media_type")
    val mediaType: String,

    @SerializedName("score")
    val score: Double,

    @SerializedName("band")
    val band: Int,

    @SerializedName("band_name")
    val bandName: String,

    @SerializedName("band_description")
    val bandDescription: String,

    @SerializedName("credits")
    val credits: Int,

    @SerializedName("credits_monthly")
    val creditsMonthly: Int
)
