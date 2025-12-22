package com.verifylabs.ai.presentation.auth.signup

import com.google.gson.annotations.SerializedName

data class SignUpVerificationResponse(

    @SerializedName("verification_token")
    val verificationToken: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("error")
    val error: String?
)
