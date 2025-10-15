package com.fatdogs.verifylabs.presentation.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.SingleLiveEvent
import com.fatdogs.verifylabs.data.network.ApiRepository
import com.fatdogs.verifylabs.presentation.plan.PlanResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: ApiRepository
) : ViewModel() {

    private val _plansResponse = MutableLiveData<Resource<List<PlanResponse>>>()
    val plansObserver: LiveData<Resource<List<PlanResponse>>> = _plansResponse

    private val errorMessage = MutableLiveData<String>()
    val getErrorMessage: LiveData<String> = errorMessage

    private val loading = MutableLiveData<Boolean>()
    val getLoading: LiveData<Boolean> = loading

    fun getPlans(secretKey: String) {
        _plansResponse.postValue(Resource.loading(null))
        loading.postValue(true)

        viewModelScope.launch {
            try {
                val response = repository.getPlans(secretKey) // now returns JsonArray
                if (response.isSuccessful) {
                    response.body()?.let { jsonArray ->
                        val plansList: List<PlanResponse> = Gson().fromJson(
                            jsonArray,
                            Array<PlanResponse>::class.java
                        ).toList()

                        _plansResponse.postValue(Resource.success(plansList))
                        loading.postValue(false)
                    } ?: run {
                        _plansResponse.postValue(Resource.error("No data received", null))
                        loading.postValue(false)
                    }
                } else {
                    _plansResponse.postValue(Resource.error(response.message(), null))
                    loading.postValue(false)
                }
            } catch (e: Exception) {
                _plansResponse.postValue(Resource.error(e.localizedMessage ?: "Unknown error", null))
                loading.postValue(false)
            }
        }
    }
}
