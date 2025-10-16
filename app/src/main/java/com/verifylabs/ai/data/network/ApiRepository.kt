package com.verifylabs.ai.data.network

import android.util.Log
import com.verifylabs.ai.data.base.BaseRepository
import com.verifylabs.ai.presentation.media.MediaType
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject

class ApiRepository @Inject internal constructor(var apiService: ApiService) : BaseRepository() {

    suspend fun getPosts() = apiService.getPosts()

    suspend fun postLogin(username: String, password: String): Response<JsonObject> {
        val jsonBody = JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
        }
        return apiService.postLogin(jsonBody)
    }



    // Signup
    suspend fun postSignUp(fullName: String, email: String, username: String, password: String,secretKey: String): Response<JsonObject> {
        val jsonBody = JsonObject().apply {
            addProperty("name", fullName)
            addProperty("email", email)
            addProperty("username", username)
            addProperty("password", password)
            addProperty("secret_key", secretKey)
        }
        return apiService.postSignUp(jsonBody)
    }


    private val bucketUrl = "https://verifylabs-temp.s3.eu-west-2.amazonaws.com"



    suspend fun uploadMedia(filePath: String, mediaType: MediaType): Response<JsonObject> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("Invalid or unreadable file: $filePath")
        }

        val timestamp = System.currentTimeMillis() / 1000
        val randomStr = (0..11).map { (('a'..'z') + ('0'..'9')).random() }.joinToString("")
        val fileExtension = file.extension.ifEmpty { "dat" }
        val s3Path = "${mediaType.folder}/${mediaType.prefix}${timestamp}_$randomStr.$fileExtension"
        val s3Url = "$bucketUrl/$s3Path"

        Log.d("MediaFragment", "uploadMedia: s3Url: $s3Url")

        val requestFile = file.asRequestBody("${mediaType.value}/*".toMediaTypeOrNull())

        return try {
            val response = apiService.uploadToS3(s3Url, requestFile)
            if (response.isSuccessful) {
                Response.success(JsonObject().apply { addProperty("uploadedUrl", s3Url) })
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                throw Exception("S3 upload failed: $errorBody")
            }
        } catch (e: Exception) {
            throw e
        }
    }





    suspend fun verifyMedia(
            username: String,
            apiKey: String,
            mediaType: String,
            mediaUrl: String
        ): Response<JsonObject> {
            val body = JsonObject().apply {
                addProperty("username", username)
                addProperty("api_key", apiKey)
                addProperty("media_type", mediaType)
                addProperty("media_url", mediaUrl)
            }
            return apiService.verifyMedia(body)
        }


    suspend fun checkCredits(username: String, apiKey: String): Response<JsonObject> {
        val jsonBody = JsonObject().apply {
            addProperty("username", username)
            addProperty("api_key", apiKey)
        }
        return apiService.checkCredits(jsonBody)
    }



    suspend fun getPlans(secretKey: String): Response<JsonArray> {
        val body = JsonObject().apply {
            addProperty("secret_key", secretKey)
        }
        return apiService.getPlans(body)
    }



}
