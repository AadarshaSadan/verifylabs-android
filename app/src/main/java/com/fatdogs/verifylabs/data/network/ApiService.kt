package com.fatdogs.verifylabs.data.network

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ApiService {
    @GET("posts/1/")
    suspend fun getPosts(): Response<JsonObject>
}