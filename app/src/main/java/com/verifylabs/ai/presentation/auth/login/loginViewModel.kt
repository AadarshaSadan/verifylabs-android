package com.verifylabs.ai.presentation.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verifylabs.ai.core.util.Resource
import com.verifylabs.ai.core.util.SingleLiveEvent
import com.verifylabs.ai.data.network.ApiRepository
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.verifylabs.ai.data.network.InternetHelper

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val internetHelper: InternetHelper
) : ViewModel() {

    private var job: Job? = null
    private val loading = MutableLiveData<Boolean>()
    private val errorMessage = MutableLiveData<String>()
    private val _loginResponse = SingleLiveEvent<Resource<JsonObject>>()

    private val _creditsResponse = SingleLiveEvent<Resource<JsonObject>>()
    fun getCreditsResponse(): LiveData<Resource<JsonObject>> = _creditsResponse


    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError("Exception: ${throwable.localizedMessage}")
    }

    fun login(username: String, password: String) {
        _loginResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        job?.cancel()
        job = viewModelScope.launch(exceptionHandler) {
            if (!internetHelper.isInternetAvailable()) {
                _loginResponse.postValue(Resource.error("No internet connection", null))
                onError("No internet connection")
                loading.postValue(false)
                return@launch
            }
            try {
                val response = repository.postLogin(username, password)
                if (response.isSuccessful) {
                    _loginResponse.postValue(Resource.success(response.body()))
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = try {
                        val errorJson = org.json.JSONObject(errorBody ?: "")
                        if (errorJson.has("error")) {
                            errorJson.getString("error")
                        } else {
                            response.message()
                        }
                    } catch (e: Exception) {
                        response.message()
                    }
                    _loginResponse.postValue(Resource.error(errorMessage, null))
                    onError("Login failed: $errorMessage")
                }
            } catch (e: Exception) {
                _loginResponse.postValue(Resource.error(e.message ?: "Unknown error", null))
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

    fun getLoginResponse(): LiveData<Resource<JsonObject>> = _loginResponse
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
