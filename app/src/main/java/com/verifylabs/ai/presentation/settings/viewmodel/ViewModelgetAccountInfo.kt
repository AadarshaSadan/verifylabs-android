package com.verifylabs.ai.presentation.settings.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.verifylabs.ai.core.util.Resource
import com.verifylabs.ai.data.network.ApiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewModelgetAccountInfo @Inject constructor(
    private val repository: ApiRepository
) : ViewModel() {

    private val _accountInfoResponse = MutableLiveData<Resource<JsonObject>>()
    val accountInfoObserver: LiveData<Resource<JsonObject>> = _accountInfoResponse

    private val errorMessage = MutableLiveData<String>()
    val getErrorMessage: LiveData<String> = errorMessage

    private val loading = MutableLiveData<Boolean>()
    val getLoading: LiveData<Boolean> = loading

    fun getAccountInfo(secretKey: String, username: String) {
        _accountInfoResponse.postValue(Resource.loading(null))
        loading.postValue(true)

        viewModelScope.launch {
            try {
                val response = repository.getWpUserInfo(secretKey, username)
                if (response.isSuccessful) {
                    response.body()?.let { jsonObject ->
                        _accountInfoResponse.postValue(Resource.success(jsonObject))
                    } ?: run {
                        _accountInfoResponse.postValue(Resource.error("No data received", null))
                    }
                } else {
                    _accountInfoResponse.postValue(Resource.error(response.message(), null))
                }
            } catch (e: Exception) {
                _accountInfoResponse.postValue(
                    Resource.error(
                        e.localizedMessage ?: "Unknown error",
                        null
                    )
                )
            } finally {
                loading.postValue(false)
            }
        }
    }
}
