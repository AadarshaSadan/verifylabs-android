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
    suspend fun postSignUp(fullName: String, email: String, username: String, password: String,secretKey: String,isVerified:Int): Response<JsonObject> {
        val jsonBody = JsonObject().apply {
            addProperty("name", fullName)
            addProperty("email", email)
            addProperty("username", username)
            addProperty("password", password)
            addProperty("secret_key", secretKey)
            addProperty("is_verified", isVerified)
            addProperty("credits", 10) // iOS parity: Match default credits
        }
        return apiService.postSignUp(jsonBody)
    }

    suspend fun resendVerificationEmail(secretKey: String, email: String): Response<JsonObject> {
        val jsonBody = JsonObject().apply {
            addProperty("secret_key", secretKey)
            addProperty("email", email)
        }
        return apiService.resendVerificationEmail(jsonBody)
    }


    private val bucketUrl = "https://verifylabs-temp.s3.eu-west-2.amazonaws.com"
    private val falsiesBucketUrl = "https://verifylabs-falsie.s3.eu-west-2.amazonaws.com"

    suspend fun reportToFalsies(originalS3Url: String, localFilePath: String, reportType: String): Response<JsonObject> {
        val cleanPath = if (localFilePath.startsWith("file:")) {
            android.net.Uri.parse(localFilePath).path ?: localFilePath
        } else {
            localFilePath
        }
        val file = File(cleanPath)
        
        if (!file.exists() || !file.canRead()) {
             // If local file is missing, we might only be able to report the URL (depends on requirements, 
             // but iOS uploads the data again. Here we assume local file is present).
            throw IllegalArgumentException("Invalid or unreadable file for report: $cleanPath")
        }

        // Extract filename from original URL or generate new one
        val originalFilename = originalS3Url.substringAfterLast("/")
        val newFilename = "${reportType}_${originalFilename}"
        
        val s3Url = "$falsiesBucketUrl/falsies/$newFilename"
        Log.d("ApiRepository", "reportToFalsies: s3Url: $s3Url")
        
        // Determine content type
        val extension = file.extension.lowercase()
        val contentType = when (extension) {
            "jpg", "jpeg", "png", "heic" -> "image/$extension"
            "mp4", "mov", "avi" -> "video/$extension"
            "wav", "mp3", "m4a" -> "audio/$extension"
            else -> "application/octet-stream"
        }

        val requestFile = file.asRequestBody(contentType.toMediaTypeOrNull())

        return try {
             // Re-using uploadToS3 since it is a generic PUT
            val response = apiService.uploadToS3(s3Url, requestFile)
            if (response.isSuccessful) {
                Response.success(JsonObject().apply { addProperty("message", "Report submitted successfully") })
            } else {
                 val errorBody = response.errorBody()?.string() ?: response.message()
                 throw Exception("Falsies upload failed: $errorBody")
            }
        } catch (e: Exception) {
            throw e
        }
    }



    suspend fun uploadMedia(filePath: String, mediaType: MediaType): Response<JsonObject> {
        val cleanPath = if (filePath.startsWith("file:")) {
            android.net.Uri.parse(filePath).path ?: filePath
        } else {
            filePath
        }
        val file = File(cleanPath)
        
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("Invalid or unreadable file: $cleanPath")
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


    suspend fun getWpUserInfo(secretKey: String, username: String): Response<JsonObject> {
        val jsonBody = JsonObject().apply {
            addProperty("secret_key", secretKey)
            addProperty("username", username)
        }
        return apiService.getWpUserInfo(jsonBody)
    }


    suspend fun consumeCredit(apiKey: String) = apiService.consumeCredit(apiKey)

    suspend fun updateUser(secretKey: String, apiKey: String, name: String? = null, email: String? = null, password: String? = null): Response<JsonObject> {
        val body = JsonObject().apply {
            addProperty("secret_key", secretKey)
            addProperty("api_key", apiKey)
            name?.let { addProperty("name", it) }
            email?.let { addProperty("email", it) }
            password?.let { addProperty("password", it) }
        }
        return apiService.updateUser(body)
    }

    suspend fun deleteUser(secretKey: String, username: String, password: String): Response<JsonObject> {
        val body = JsonObject().apply {
            addProperty("secret_key", secretKey)
            addProperty("username", username)
            addProperty("password", password)
        }
        return apiService.deleteUser(body)
    }
}
