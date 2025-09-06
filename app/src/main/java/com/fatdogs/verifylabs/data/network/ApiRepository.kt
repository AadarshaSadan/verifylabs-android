package com.fatdogs.verifylabs.data.network

import com.fatdogs.verifylabs.data.base.BaseRepository
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
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

    // Upload media
    suspend fun uploadMedia(filePath: String, mediaType: String): Response<JsonObject> {
        val file = File(filePath)
        val requestFile = RequestBody.create(
            if (mediaType == "image") "image/*".toMediaTypeOrNull() else "video/*".toMediaTypeOrNull(),
            file
        )
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val typeBody = RequestBody.create("text/plain".toMediaTypeOrNull(), mediaType)

        return apiService.uploadMedia(body, typeBody)
    }
}
