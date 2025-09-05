package com.fatdogs.verifylabs.data.network

import com.fatdogs.verifylabs.data.base.BaseRepository
import javax.inject.Inject

class ApiRepository @Inject internal constructor(var apiService: ApiService) : BaseRepository() {
    suspend fun getPosts() =  apiService.getPosts()


}