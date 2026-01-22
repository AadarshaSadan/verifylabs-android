package com.verifylabs.ai.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.verifylabs.ai.core.util.Resource
import com.verifylabs.ai.core.util.SingleLiveEvent
import com.verifylabs.ai.data.network.ApiRepository
import com.verifylabs.ai.presentation.media.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MediaViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {

    // private var job: Job? = null // Removed for parallel support
    private val loading = MutableLiveData<Boolean>()
    private val errorMessage = MutableLiveData<String>()
    private val _uploadResponse = SingleLiveEvent<Resource<JsonObject>>()
    private val _verifyResponse = MutableSharedFlow<Resource<JsonObject>>()
    val verifyResponseFlow = _verifyResponse.asSharedFlow()
    private val _creditsResponse = SingleLiveEvent<Resource<JsonObject>>()
    private val _creditConsumed = MutableSharedFlow<Unit>() // Signal for credit deduction
    val creditConsumedFlow = _creditConsumed.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError("Exception: ${throwable.localizedMessage}")
    }

    fun uploadMedia(filePath: String, mediaType: MediaType) {
        _uploadResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        viewModelScope.launch(exceptionHandler) {
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
        // _verifyResponse.postValue(Resource.loading(null)) // Optional for parallel
        viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.verifyMedia(username, apiKey, mediaType, mediaUrl)
                if (response.isSuccessful) {
                    _verifyResponse.emit(Resource.success(response.body()))
                    
                    // Consume credit automatically (Parity with iOS)
                    consumeCredit(apiKey)
                } else {
                    _verifyResponse.emit(Resource.error(response.message(), null))
                    onError("Verification failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _verifyResponse.emit(Resource.error(e.message ?: "Unknown error", null))
                onError(e.message ?: "Unknown error")
            } finally {
                // loading.postValue(false)
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
                    _creditsResponse.postValue(
                            Resource.error("Failed to fetch credits: ${response.message()}", null)
                    )
                }
            } catch (e: Exception) {
                _creditsResponse.postValue(Resource.error(e.message ?: "Unknown error", null))
            }
        }
    }

    private fun consumeCredit(apiKey: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.consumeCredit(apiKey)
                if (response.isSuccessful) {
                    _creditConsumed.emit(Unit)
                }
            } catch (e: Exception) {
                // Background credit consumption failure shouldn't block UI
                android.util.Log.e("MediaViewModel", "Credit consumption failed", e)
            }
        }
    }

    fun getUploadResponse(): LiveData<Resource<JsonObject>> = _uploadResponse
    // LiveData getter for backward compatibility (optional, but fragment needs current flow)
    @Deprecated("Use verifyResponseFlow instead")
    fun getVerifyResponse(): LiveData<Resource<JsonObject>> = MutableLiveData()
    fun getCreditsResponse(): LiveData<Resource<JsonObject>> = _creditsResponse
    fun getLoading(): LiveData<Boolean> = loading
    fun getErrorMessage(): LiveData<String> = errorMessage

    private fun onError(message: String) {
        errorMessage.postValue(message)
        loading.postValue(false)
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope.cancel() // Alternatively, let viewModelScope handle completion
    }
}
