package com.verifylabs.ai.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verifylabs.ai.core.util.Resource
import com.verifylabs.ai.core.util.SingleLiveEvent
import com.verifylabs.ai.data.network.ApiRepository
import com.verifylabs.ai.presentation.media.MediaType
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {

    private var job: Job? = null
    private val loading = MutableLiveData<Boolean>()
    private val errorMessage = MutableLiveData<String>()
    private val _uploadResponse = SingleLiveEvent<Resource<JsonObject>>()
    private val _verifyResponse = SingleLiveEvent<Resource<JsonObject>>()
    private val _creditsResponse = SingleLiveEvent<Resource<JsonObject>>()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError("Exception: ${throwable.localizedMessage}")
    }

    fun uploadMedia(filePath: String, mediaType: MediaType) {
        _uploadResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        viewModelScope.launch {
            try {
                val response = repository.uploadMedia(filePath, mediaType)
                _uploadResponse.postValue(Resource.success(response.body()))
            } catch (e: Exception) {
                _uploadResponse.postValue(Resource.error(e.message ?: "Unknown error", null))
                errorMessage.postValue(e.message ?: "Unknown error")
            } finally {
                loading.postValue(false)
            }
        }
    }

    fun verifyMedia(username: String, apiKey: String, mediaType: String, mediaUrl: String) {
        _verifyResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        job?.cancel()
        job = viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.verifyMedia(username, apiKey, mediaType, mediaUrl)
                if (response.isSuccessful) {
                    _verifyResponse.postValue(Resource.success(response.body()))
                } else {
                    _verifyResponse.postValue(Resource.error(response.message(), null))
                    onError("Verification failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _verifyResponse.postValue(Resource.error(e.message ?: "Unknown error", null))
                onError(e.message ?: "Unknown error")
            } finally {
                loading.postValue(false)
            }
        }
    }

    // -------------------- CHECK CREDITS --------------------
    fun checkCredits(username: String, apiKey: String) {
        _creditsResponse.postValue(Resource.loading(null))
        viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.checkCredits(username, apiKey)
                if (response.isSuccessful && response.body() != null) {
                    _creditsResponse.postValue(Resource.success(response.body()))
                } else {
                    _creditsResponse.postValue(Resource.error("Failed to fetch credits: ${response.message()}", null))
                }
            } catch (e: Exception) {
                _creditsResponse.postValue(Resource.error(e.message ?: "Unknown error", null))
            }
        }
    }

    fun getUploadResponse(): LiveData<Resource<JsonObject>> = _uploadResponse
    fun getVerifyResponse(): LiveData<Resource<JsonObject>> = _verifyResponse
    fun getCreditsResponse(): LiveData<Resource<JsonObject>> = _creditsResponse
    fun getLoading(): LiveData<Boolean> = loading
    fun getErrorMessage(): LiveData<String> = errorMessage

    private fun onError(message: String) {
        errorMessage.postValue(message)
        loading.postValue(false)
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }
}
