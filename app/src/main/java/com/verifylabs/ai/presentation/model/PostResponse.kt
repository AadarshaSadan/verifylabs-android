package com.verifylabs.ai.presentation.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
data class PostResponse(
    @SerializedName("userId") val userId: Int,
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String
) : Serializable
