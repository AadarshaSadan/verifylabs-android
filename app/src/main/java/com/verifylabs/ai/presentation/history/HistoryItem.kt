package com.verifylabs.ai.presentation.history

data class HistoryItem(
    val id: Int,
    val title: String,
    val type: String,   // "Image", "Video", "Audio"
    val date: String,
    val aiScore: Int,
    val mode: String,     // "Human" or "Machine"
    val mediaUri: String? = null
)
