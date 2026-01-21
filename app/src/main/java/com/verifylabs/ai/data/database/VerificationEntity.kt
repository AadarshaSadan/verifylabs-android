package com.verifylabs.ai.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "verification_history")
data class VerificationEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val mediaType: String, // "Image", "Video", "Audio"
        val mediaUri: String?, // Local file path or URI
        val mediaThumbnail: String?, // Thumbnail path for images/videos

        // Verification results
        val band: Int, // 1-5
        val bandName: String, // "Human Made", etc.
        val bandDescription: String,
        val aiScore: Double, // The score from API

        // Stats
        val fileSizeKb: Long?,
        val resolution: String?,
        val quality: Int?,

        // Metadata
        val timestamp: Long, // When verified
        val username: String // Who verified it
)
