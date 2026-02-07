package com.verifylabs.ai.presentation.auth.forgotpassword

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verifylabs.ai.core.util.Resource
import com.verifylabs.ai.data.network.ApiRepository
import com.verifylabs.ai.data.network.InternetHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ForgotPasswordViewModel
@Inject
constructor(private val repository: ApiRepository, private val internetHelper: InternetHelper) :
        ViewModel() {

    private val _resetPasswordResponse = MutableLiveData<Resource<String>>()
    val resetPasswordResponse: LiveData<Resource<String>> = _resetPasswordResponse

    fun requestPasswordReset(email: String) {
        _resetPasswordResponse.value = Resource.loading(null)

        viewModelScope.launch {
            if (!internetHelper.isInternetAvailable()) {
                _resetPasswordResponse.value = Resource.error("No internet connection", null)
                return@launch
            }

            try {
                val result = repository.requestPasswordReset(email)
                _resetPasswordResponse.value = result
            } catch (e: Exception) {
                _resetPasswordResponse.value = Resource.error(e.message ?: "Unknown error", null)
            }
        }
    }
}
