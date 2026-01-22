package com.verifylabs.ai.core.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object HistoryFileManager {
    private const val TAG = "HistoryFileManager"
    private const val HISTORY_DIR = "history"
    private const val MEDIA_DIR = "media"
    private const val THUMBNAILS_DIR = "thumbnails"

    fun saveMedia(context: Context, sourceUri: Uri, mediaType: String): String? {
        try {
            val historyDir = File(context.filesDir, HISTORY_DIR)
            val mediaDir = File(historyDir, MEDIA_DIR)
            if (!mediaDir.exists()) mediaDir.mkdirs()

            val extension = when(mediaType.lowercase()) {
                "video" -> "mp4"
                "audio" -> "m4a" // or wav depending on recording
                else -> "jpg"
            }
            
            val fileName = "${UUID.randomUUID()}.$extension"
            val destFile = File(mediaDir, fileName)

            if (sourceUri.scheme == "file" && sourceUri.path != null) {
                // Direct file copy for file:// URIs to avoid sensitive ContentResolver limitations
                val sourceFile = File(sourceUri.path!!)
                if (sourceFile.exists()) {
                     sourceFile.copyTo(destFile, overwrite = true)
                } else {
                     // Fallback to stream if file check fails (weird path?)
                     context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            Log.d(TAG, "Saved media to: ${destFile.absolutePath}")
            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media", e)
            return null
        }
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrEmpty()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted file: $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file", e)
        }
    }

    fun getTotalStorageSize(context: Context): Long {
        val historyDir = File(context.filesDir, HISTORY_DIR)
        return getFolderSize(historyDir)
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0
        if (!file.isDirectory) return file.length()
        var size: Long = 0
        file.listFiles()?.forEach {
            size += getFolderSize(it)
        }
        return size
    }
}
