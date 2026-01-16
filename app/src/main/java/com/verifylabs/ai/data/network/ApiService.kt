package com.verifylabs.ai.data.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("posts/1/")
    suspend fun getPosts(): Response<JsonObject>

    @POST("login")
    suspend fun postLogin(
        @Body body: JsonObject
    ): Response<JsonObject>


    @POST("wp_users")
    suspend fun postSignUp(@Body body: JsonObject): Response<JsonObject>


    // Direct upload to S3
    @PUT
    suspend fun uploadToS3(
        @Url url: String,
        @Body  requestBody: RequestBody
    ): Response<ResponseBody> // The response type can be adjusted based on your needs


    @POST("verify")
    suspend fun verifyMedia(
        @Body body: JsonObject
    ): Response<JsonObject>


   @POST("check")
   suspend fun checkCredits(
       @Body body: JsonObject
   ): Response<JsonObject>


    @POST("plans")
    suspend fun getPlans(
        @Body body: JsonObject
    ): Response<JsonArray>


    // WordPress user info
    @POST("wp_userinfo")
    suspend fun getWpUserInfo(
        @Body body: JsonObject
    ): Response<JsonObject>


}
