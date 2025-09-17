package com.fatdogs.verifylabs.presentation.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.SingleLiveEvent
import com.fatdogs.verifylabs.data.network.ApiRepository
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {

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
            try {
                val response = repository.postLogin(username, password)
                if (response.isSuccessful) {
                    _loginResponse.postValue(Resource.success(response.body()))
                } else {
                    _loginResponse.postValue(Resource.error(response.message(), null))
                    onError("Login failed: ${response.message()}")
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
