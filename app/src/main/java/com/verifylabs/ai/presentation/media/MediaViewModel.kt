package com.verifylabs.ai.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@HiltViewModel
class MediaViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {

    private val loading = MutableLiveData<Boolean>()
    private val errorMessage = MutableLiveData<String>()

    // Changed to StateFlow for persistence
    private val _uploadResponse = MutableStateFlow<Resource<JsonObject>?>(null)
    val uploadResponseFlow = _uploadResponse.asStateFlow()

    private val _verifyResponse = MutableStateFlow<Resource<JsonObject>?>(null)
    val verifyResponseFlow = _verifyResponse.asStateFlow()

    private val _creditsResponse = SingleLiveEvent<Resource<JsonObject>>()
    private val _creditConsumed = MutableSharedFlow<Unit>()
    val creditConsumedFlow = _creditConsumed.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError("Exception: ${throwable.localizedMessage}")
    }

    private val _reportResponse = SingleLiveEvent<Resource<JsonObject>>()
    val reportResponse: LiveData<Resource<JsonObject>> = _reportResponse

    var lastUploadedS3Url: String? = null
        private set

    var isResultHandled: Boolean = false

    fun uploadMedia(filePath: String, mediaType: MediaType) {
        isResultHandled = false
        _verifyResponse.value = null // Clear previous verification result to prevent stale replay
        _uploadResponse.value = Resource.loading(null)
        loading.postValue(true)
        viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.uploadMedia(filePath, mediaType)
                if (response.isSuccessful && response.body()?.has("uploadedUrl") == true) {
                    lastUploadedS3Url = response.body()?.get("uploadedUrl")?.asString
                    _uploadResponse.value = Resource.success(response.body())
                } else {
                    val errorMsg = "Upload failed: ${response.message()}"
                    _uploadResponse.value = Resource.error(errorMsg, null)
                    errorMessage.postValue(errorMsg)
                }
            } catch (e: Exception) {
                _uploadResponse.value = Resource.error(e.message ?: "Unknown upload error", null)
                errorMessage.postValue(e.message ?: "Unknown upload error")
            } finally {
                loading.postValue(false)
            }
        }
    }

    fun reportResult(reportType: String, localFilePath: String) {
        val s3Url = lastUploadedS3Url
        if (s3Url.isNullOrEmpty()) {
            _reportResponse.postValue(Resource.error("No media uploaded to report", null))
            return
        }

        _reportResponse.postValue(Resource.loading(null))
        viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.reportToFalsies(s3Url, localFilePath, reportType)
                if (response.isSuccessful) {
                    _reportResponse.postValue(Resource.success(response.body()))
                } else {
                    _reportResponse.postValue(Resource.error(response.message(), null))
                }
            } catch (e: Exception) {
                _reportResponse.postValue(Resource.error(e.message ?: "Report failed", null))
            }
        }
    }

    fun verifyMedia(username: String, apiKey: String, mediaType: String, mediaUrl: String) {

        isResultHandled = false
        _verifyResponse.value = Resource.loading(null) // Ensure loading state immediately to clear stale success
        if (username.isEmpty() || apiKey.isEmpty()) {
            onError("Authentication missing. Please log in again.")
            _verifyResponse.value = Resource.error("Authentication missing", null)
            return
        }

        viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.verifyMedia(username, apiKey, mediaType, mediaUrl)
                if (response.isSuccessful) {
                    _verifyResponse.value = Resource.success(response.body())

                    // Consume credit automatically (Parity with iOS)
                    consumeCredit(apiKey)
                } else {
                    val code = response.code()
                    val msg = response.message()
                    if (code == 402) {
                        _verifyResponse.value =
                                Resource(
                                        com.verifylabs.ai.core.util.Status.INSUFFICIENT_CREDITS,
                                        null,
                                        "Insufficient Credits"
                                )
                    } else {
                        val fullError = "Verification failed (Code $code): $msg"
                        _verifyResponse.value = Resource.error(fullError, null)
                        onError(fullError)
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown verification error"
                _verifyResponse.value = Resource.error(errorMsg, null)
                onError(errorMsg)
            }
        }
    }

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
                android.util.Log.e("MediaViewModel", "Credit consumption failed", e)
            }
        }
    }

    fun getUploadResponse(): LiveData<Resource<JsonObject>> =
            _uploadResponse.filterNotNull().asLiveData()

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
    }
}
