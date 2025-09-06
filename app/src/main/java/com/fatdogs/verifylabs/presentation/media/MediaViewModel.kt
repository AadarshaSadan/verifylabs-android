package com.fatdogs.verifylabs.presentation.viewmodel

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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {

    private val _uploadResponse = SingleLiveEvent<Resource<JsonObject>>()
    val uploadResponse: LiveData<Resource<JsonObject>> = _uploadResponse

    private val loading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = loading

    private val errorMessage = MutableLiveData<String>()
    val error: LiveData<String> = errorMessage

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError("Exception: ${throwable.localizedMessage}")
    }

    fun uploadMedia(filePath: String, mediaType: String) {
        _uploadResponse.postValue(Resource.loading(null))
        loading.postValue(true)

        viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.uploadMedia(filePath, mediaType)
                if (response.isSuccessful) {
                    _uploadResponse.postValue(Resource.success(response.body()))
                } else {
                    _uploadResponse.postValue(Resource.error(response.message(), null))
                    onError("Error: ${response.message()}")
                }
                loading.postValue(false)
            } catch (e: Exception) {
                _uploadResponse.postValue(Resource.error(e.message.toString(), null))
                onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun onError(msg: String) {
        errorMessage.postValue(msg)
        loading.postValue(false)
    }
}
