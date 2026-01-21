package com.verifylabs.ai.presentation.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.verifylabs.ai.data.database.VerificationEntity
import com.verifylabs.ai.data.repository.VerificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: VerificationRepository
) : ViewModel() {
    
    val allHistory: LiveData<List<HistoryItem>> = 
        repository.getAllHistory()
            .map { entities -> entities.map { it.toHistoryItem() } }
            .asLiveData()
    
    fun getHistoryByType(type: String): LiveData<List<HistoryItem>> {
        return repository.getHistoryByType(type)
            .map { entities -> entities.map { it.toHistoryItem() } }
            .asLiveData()
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id.toLong())
        }
    }
    
    private fun VerificationEntity.toHistoryItem() = HistoryItem(
        id = id.toInt(),
        title = bandName,
        type = mediaType,
        date = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault())
            .format(Date(timestamp)),
        aiScore = (aiScore * 100).toInt(),
        mode = if (band <= 2) "Human" else "Machine",
        mediaUri = mediaUri
    )
}
