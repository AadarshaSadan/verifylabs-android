package com.verifylabs.ai.presentation.auth.signup

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
class SignUpViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val internetHelper: InternetHelper
) : ViewModel() {

    private var job: Job? = null
    private val loading = MutableLiveData<Boolean>()
    private val errorMessage = MutableLiveData<String>()
    private val _signUpResponse = SingleLiveEvent<Resource<JsonObject>>()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError("Exception: ${throwable.localizedMessage}")
    }

    fun signUp(fullName: String, email: String, username: String, password: String,secretKey: String,isVerified:Int) {
        _signUpResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        job?.cancel()
        job = viewModelScope.launch(exceptionHandler) {
            if (!internetHelper.isInternetAvailable()) {
                _signUpResponse.postValue(Resource.error("No internet connection", null))
                onError("No internet connection")
                loading.postValue(false)
                return@launch
            }
            try {
                val response = repository.postSignUp(fullName, email, username, password,secretKey,isVerified)
                if (response.isSuccessful) {
                    _signUpResponse.postValue(Resource.success(response.body()))
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
                    _signUpResponse.postValue(Resource.error(errorMessage, null))
                    onError("Sign up failed: $errorMessage")
                }
            } catch (e: Exception) {
                _signUpResponse.postValue(Resource.error(e.message ?: "Unknown error", null))
            } finally {
                loading.postValue(false)
            }
        }
    }

    fun getSignUpResponse(): LiveData<Resource<JsonObject>> = _signUpResponse
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
