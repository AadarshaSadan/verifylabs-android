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

    private val _updateProfileResponse = MutableLiveData<Resource<JsonObject>>()
    val updateProfileObserver: LiveData<Resource<JsonObject>> = _updateProfileResponse

    private val _deleteAccountResponse = MutableLiveData<Resource<JsonObject>>()
    val deleteAccountObserver: LiveData<Resource<JsonObject>> = _deleteAccountResponse

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
                _accountInfoResponse.postValue(Resource.error(e.localizedMessage ?: "Unknown error", null))
                // Do not show error message toast for this background fetch to avoid annoying user
                // errorMessage.postValue(e.localizedMessage ?: "Unknown error")
            } finally {
                loading.postValue(false)
            }
        }
    }

    fun updateProfile(secretKey: String, apiKey: String, name: String?, email: String?) {
        _updateProfileResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        viewModelScope.launch {
            try {
                val response = repository.updateUser(secretKey, apiKey, name = name, email = email)
                if (response.isSuccessful) {
                    _updateProfileResponse.postValue(Resource.success(response.body()))
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    _updateProfileResponse.postValue(Resource.error(errorMsg, null))
                    errorMessage.postValue("Failed to update profile: $errorMsg")
                }
            } catch (e: Exception) {
                _updateProfileResponse.postValue(Resource.error(e.localizedMessage ?: "Error", null))
                errorMessage.postValue(e.localizedMessage ?: "Unknown error")
            } finally {
                loading.postValue(false)
            }
        }
    }

    fun changePassword(secretKey: String, apiKey: String, newPassword: String) {
        _updateProfileResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        viewModelScope.launch {
            try {
                // Change password uses same endpoint as update profile
                val response = repository.updateUser(secretKey, apiKey, password = newPassword)
                if (response.isSuccessful) {
                    _updateProfileResponse.postValue(Resource.success(response.body()))
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    _updateProfileResponse.postValue(Resource.error(errorMsg, null))
                    errorMessage.postValue("Failed to change password: $errorMsg")
                }
            } catch (e: Exception) {
                _updateProfileResponse.postValue(Resource.error(e.localizedMessage ?: "Error", null))
                errorMessage.postValue(e.localizedMessage ?: "Unknown error")
            } finally {
                loading.postValue(false)
            }
        }
    }

    fun deleteAccount(secretKey: String, username: String, password: String) {
        _deleteAccountResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        viewModelScope.launch {
            try {
                val response = repository.deleteUser(secretKey, username, password)
                if (response.isSuccessful) {
                    _deleteAccountResponse.postValue(Resource.success(response.body()))
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    _deleteAccountResponse.postValue(Resource.error(errorMsg, null))
                    errorMessage.postValue("Failed to delete account: $errorMsg")
                }
            } catch (e: Exception) {
                _deleteAccountResponse.postValue(Resource.error(e.localizedMessage ?: "Error", null))
                errorMessage.postValue(e.localizedMessage ?: "Unknown error")
            } finally {
                loading.postValue(false)
            }
        }
    }
}
