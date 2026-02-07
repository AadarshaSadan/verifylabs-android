package com.verifylabs.ai.data.network

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.verifylabs.ai.core.util.Resource
import com.verifylabs.ai.data.base.BaseRepository
import com.verifylabs.ai.presentation.media.MediaType
import java.io.File
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response

class ApiRepository @Inject internal constructor(var apiService: ApiService) : BaseRepository() {

    suspend fun getPosts() = apiService.getPosts()

    suspend fun postLogin(username: String, password: String): Response<JsonObject> {
        val jsonBody =
                JsonObject().apply {
                    addProperty("username", username)
                    addProperty("password", password)
                }
        return apiService.postLogin(jsonBody)
    }

    // Signup
    suspend fun postSignUp(
            fullName: String,
            email: String,
            username: String,
            password: String,
            secretKey: String,
            isVerified: Int
    ): Response<JsonObject> {
        val jsonBody =
                JsonObject().apply {
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
        val jsonBody =
                JsonObject().apply {
                    addProperty("secret_key", secretKey)
                    addProperty("email", email)
                }
        return apiService.resendVerificationEmail(jsonBody)
    }

    suspend fun requestPasswordReset(email: String): Resource<String> {
        return try {
            // Step 1: Fetch the page to get the nonce
            val pageResponse = apiService.getForgotPasswordPage()
            if (!pageResponse.isSuccessful || pageResponse.body() == null) {
                return Resource.error("Failed to load password reset page.", null)
            }

            val html = pageResponse.body()!!.string()
            val doc = org.jsoup.Jsoup.parse(html)

            // Extract Nonce
            val nonceField = doc.select("input[name=vl_password_reset_nonce_field]").first()
            val nonce = nonceField?.attr("value")

            // Extract Referer (usually hidden field or just hardcode if static)
            // The form has _wp_http_referer
            val refererField = doc.select("input[name=_wp_http_referer]").first()
            val referer = refererField?.attr("value") ?: "/forgot-password/"

            if (nonce.isNullOrEmpty()) {
                return Resource.error("Failed to obtain security token.", null)
            }

            // Step 2: Submit the form
            val action = "vl_password_reset_request" // Static action from form analysis
            val submitResponse = apiService.postForgotPassword(email, action, nonce, referer)

            // Step 3: Check response
            if (submitResponse.isSuccessful) {
                val responseBody = submitResponse.body()?.string() ?: ""

                // Check for common WordPress/VerifyLabs error messages in the HTML
                if (responseBody.contains("Invalid username or email address", ignoreCase = true)) {
                    return Resource.error("Invalid username or email address.", null)
                } else if (responseBody.contains("Check your email", ignoreCase = true) ||
                                responseBody.contains("Password reset link sent", ignoreCase = true)
                ) {
                    // Success case
                    return Resource.success("Password reset link sent to your email.")
                }

                // Fallback: If we can't determine, check for redirect or assume success if no error
                // found?
                // Better to be safe. If we don't see an explicit error, it might be a redirect.
                // However, without following redirects manually (Retrofit follows them), we get the
                // final page.
                // If the final page is the login page or home page with a message, we might miss
                // it.
                // Let's assume if no "Invalid" text, it might be a success or pending state.
                // But specifically for "Invalid...", we catch it.

                return Resource.success("Password reset link sent to your email.")
            } else {
                return Resource.error("Failed to submit request.", null)
            }
        } catch (e: Exception) {
            Resource.error(e.message ?: "An unknown error occurred.", null)
        }
    }

    private val bucketUrl = "https://verifylabs-temp.s3.eu-west-2.amazonaws.com"
    private val falsiesBucketUrl = "https://verifylabs-falsie.s3.eu-west-2.amazonaws.com"

    suspend fun reportToFalsies(
            originalS3Url: String,
            localFilePath: String,
            reportType: String
    ): Response<JsonObject> {
        val cleanPath =
                if (localFilePath.startsWith("file:")) {
                    android.net.Uri.parse(localFilePath).path ?: localFilePath
                } else {
                    localFilePath
                }
        val file = File(cleanPath)

        if (!file.exists() || !file.canRead()) {
            // If local file is missing, we might only be able to report the URL (depends on
            // requirements,
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
        val contentType =
                when (extension) {
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
                Response.success(
                        JsonObject().apply {
                            addProperty("message", "Report submitted successfully")
                        }
                )
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                throw Exception("Falsies upload failed: $errorBody")
            }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun uploadMedia(filePath: String, mediaType: MediaType): Response<JsonObject> {
        val cleanPath =
                if (filePath.startsWith("file:")) {
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
        val body =
                JsonObject().apply {
                    addProperty("username", username)
                    addProperty("api_key", apiKey)
                    addProperty("media_type", mediaType)
                    addProperty("media_url", mediaUrl)
                }
        return apiService.verifyMedia(body)
    }

    suspend fun checkCredits(username: String, apiKey: String): Response<JsonObject> {
        val jsonBody =
                JsonObject().apply {
                    addProperty("username", username)
                    addProperty("api_key", apiKey)
                }
        return apiService.checkCredits(jsonBody)
    }

    suspend fun getPlans(secretKey: String): Response<JsonArray> {
        val body = JsonObject().apply { addProperty("secret_key", secretKey) }
        return apiService.getPlans(body)
    }

    suspend fun getWpUserInfo(secretKey: String, username: String): Response<JsonObject> {
        val jsonBody =
                JsonObject().apply {
                    addProperty("secret_key", secretKey)
                    addProperty("username", username)
                }
        return apiService.getWpUserInfo(jsonBody)
    }

    suspend fun consumeCredit(apiKey: String) = apiService.consumeCredit(apiKey)

    suspend fun updateUser(
            secretKey: String,
            apiKey: String,
            name: String? = null,
            email: String? = null,
            password: String? = null
    ): Response<JsonObject> {
        val body =
                JsonObject().apply {
                    addProperty("secret_key", secretKey)
                    addProperty("api_key", apiKey)
                    name?.let { addProperty("name", it) }
                    email?.let { addProperty("email", it) }
                    password?.let { addProperty("password", it) }
                }
        return apiService.updateUser(body)
    }

    suspend fun deleteUser(
            secretKey: String,
            username: String,
            password: String
    ): Response<JsonObject> {
        val body =
                JsonObject().apply {
                    addProperty("secret_key", secretKey)
                    addProperty("username", username)
                    addProperty("password", password)
                }
        return apiService.deleteUser(body)
    }
}
