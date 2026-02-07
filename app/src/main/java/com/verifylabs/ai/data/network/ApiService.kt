package com.verifylabs.ai.data.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

        @GET("posts/1/") suspend fun getPosts(): Response<JsonObject>

        @POST("login") suspend fun postLogin(@Body body: JsonObject): Response<JsonObject>

        @POST("wp_users") suspend fun postSignUp(@Body body: JsonObject): Response<JsonObject>

        // Forgot Password - Step 1: Get Page for Nonce
        @GET("https://verifylabs.ai/forgot-password/")
        suspend fun getForgotPasswordPage(): Response<ResponseBody>

        // Forgot Password - Step 2: Submit Form
        @FormUrlEncoded
        @POST("https://verifylabs.ai/wp-admin/admin-post.php")
        suspend fun postForgotPassword(
                @Field("user_login") userLogin: String,
                @Field("action") action: String,
                @Field("vl_password_reset_nonce_field") nonce: String,
                @Field("_wp_http_referer") referer: String
        ): Response<ResponseBody>

        // Direct upload to S3
        @PUT
        suspend fun uploadToS3(
                @Url url: String,
                @Body requestBody: RequestBody
        ): Response<ResponseBody> // The response type can be adjusted based on your needs

        @POST("verify") suspend fun verifyMedia(@Body body: JsonObject): Response<JsonObject>

        @POST("check") suspend fun checkCredits(@Body body: JsonObject): Response<JsonObject>

        @POST("plans") suspend fun getPlans(@Body body: JsonObject): Response<JsonArray>

        // WordPress user info
        @POST("wp_userinfo") suspend fun getWpUserInfo(@Body body: JsonObject): Response<JsonObject>

        // Credit consumption
        @GET("usecredit")
        suspend fun consumeCredit(@Query("api_key") apiKey: String): Response<ResponseBody>

        // Update User Profile
        @POST("update_user") suspend fun updateUser(@Body body: JsonObject): Response<JsonObject>

        // Delete User
        @POST("delete_user") suspend fun deleteUser(@Body body: JsonObject): Response<JsonObject>

        // Resend Verification Email
        @POST("resend_verification")
        suspend fun resendVerificationEmail(@Body body: JsonObject): Response<JsonObject>
}
