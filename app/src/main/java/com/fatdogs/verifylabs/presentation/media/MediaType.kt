package com.fatdogs.verifylabs.presentation.media

enum class MediaType(val value: String, val folder: String, val prefix: String) {
    IMAGE("image", "images", "image_"),
    VIDEO("video", "video", "recording_"),
    AUDIO("audio", "audio", "recording_")
}