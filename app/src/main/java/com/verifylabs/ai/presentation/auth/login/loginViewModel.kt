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
import com.verifylabs.ai.data.base.PreferenceHelper

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val internetHelper: InternetHelper,
    private val preferenceHelper: PreferenceHelper
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
                // 1. Perform Backend Login
                val response = repository.postLogin(username, password)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    
                    // Parse Login Response
                    val apiKey = body.get("api_key")?.asString ?: ""
                    val credits = body.get("credits")?.asInt ?: 0
                    val creditsMonthly = body.get("credits_monthly")?.asInt ?: 0
                    val totalCredits = credits + creditsMonthly
                    
                    // 2. Identify with RevenueCat
                    com.revenuecat.purchases.Purchases.sharedInstance.logIn(username, object : com.revenuecat.purchases.interfaces.LogInCallback {
                         override fun onReceived(customerInfo: com.revenuecat.purchases.CustomerInfo, created: Boolean) {
                            // Set attributes for webhooks
                            com.revenuecat.purchases.Purchases.sharedInstance.setAttributes(mapOf(
                                "verifylabs_api_key" to apiKey,
                                "verifylabs_username" to username
                            ))
                        }
                        override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                            // Log error but don't block login flow
                            android.util.Log.e("LoginViewModel", "RevenueCat login failed: ${error.message}")
                        }
                    })

                    // 3. Authoritative Credit Sync (Update local immediately)
                    preferenceHelper.setCreditReamaining(totalCredits)
                    
                    // 4. Check Email Verification Status
                    try {
                        val userInfoResponse = repository.getWpUserInfo(com.verifylabs.ai.core.util.Constants.SECRET_KEY, username)
                        if (userInfoResponse.isSuccessful && userInfoResponse.body() != null) {
                            val userInfo = userInfoResponse.body()!!
                            val isEmailVerified = userInfo.get("is_email_verified")?.asBoolean ?: false
                            // Store verification status if needed (currently iOS clears profile completion helper)
                             if (isEmailVerified) {
                                // Logic to clear profile completion if implemented in Android
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("LoginViewModel", "Failed to fetch user info for email verification: ${e.message}")
                    }

                    // Final Success
                    _loginResponse.postValue(Resource.success(body))
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = try {
                        val errorJson = org.json.JSONObject(errorBody ?: "")
                        if (errorJson.has("error")) {
                            errorJson.getString("error")
                        } else {
                            if (response.message().isNullOrEmpty()) "Error ${response.code()}" else response.message()
                        }
                    } catch (e: Exception) {
                        if (response.message().isNullOrEmpty()) "Error ${response.code()}" else response.message()
                    }
                    _loginResponse.postValue(Resource.error(errorMessage, null))
                    onError(errorMessage)
                }
            } catch (e: java.io.IOException) {
                // Network error (timeout, connection dropped, etc.)
                val msg = "Network error. Please check your connection."
                _loginResponse.postValue(Resource.error(msg, null))
                onError(msg)
            } catch (e: Exception) {
                // Other errors
                val msg = e.localizedMessage ?: "An unexpected error occurred"
                _loginResponse.postValue(Resource.error(msg, null))
                onError(msg)
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
