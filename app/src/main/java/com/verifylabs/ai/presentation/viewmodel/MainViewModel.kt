package com.verifylabs.ai.presentation.viewmodel

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

@HiltViewModel
class MainViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {
    private val errorMessage = MutableLiveData<String>()
    private val loading = MutableLiveData<Boolean>()
    private var job: Job? = null

    private val _postsResponse = SingleLiveEvent<Resource<JsonObject>>()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError("Exception handled: ${throwable.localizedMessage}")
    }
    private val _imageUploadResponse = SingleLiveEvent<Resource<JsonObject>>()

    //here below the functions

    fun getPosts(){
        _postsResponse.postValue(Resource.loading(null))
        loading.postValue(true)
        job?.cancel()
        job = viewModelScope.launch(exceptionHandler) {
            try {
                val response = repository.getPosts()
                if (response.isSuccessful) {
                    _postsResponse.postValue(Resource.success(response.body()))
                    loading.postValue(false)
                } else {
                    onError("Error : ${response.message()} ")
                    _postsResponse.postValue(Resource.error(response.message(), null))
                }
            } catch (e: Exception) {
                _postsResponse.postValue(Resource.error(e.message.toString(), null))
            }
        }
    }



    fun getPostsAPIObserver(): LiveData<Resource<JsonObject>> = _postsResponse

    fun getErrorMessage(): LiveData<String> = errorMessage

    fun getLoading(): LiveData<Boolean> = loading



    private fun onError(message: String) {
        errorMessage.postValue(message)
        loading.postValue(false)
    }



    override fun onCleared() {
        super.onCleared()
    }
}
