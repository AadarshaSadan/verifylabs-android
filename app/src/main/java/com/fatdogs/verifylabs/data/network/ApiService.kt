package com.fatdogs.verifylabs.data.network

import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("posts/1/")
    suspend fun getPosts(): Response<JsonObject>

    @POST("login")
    suspend fun postLogin(
        @Body body: JsonObject
    ): Response<JsonObject>

    // Upload media (image/video)
    @Multipart
    @POST("verifyMedia") // replace with your actual endpoint
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("media_type") mediaType: RequestBody
    ): Response<JsonObject>
}
