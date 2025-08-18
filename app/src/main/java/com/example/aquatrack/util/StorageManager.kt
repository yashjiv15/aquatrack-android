package com.example.aquatrack.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

object StorageManager {
    fun getRecordingsDirectory(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 and above: Use app-specific directory
            File(context.filesDir, "recordings").apply { mkdirs() }
        } else {
            // Android 9 and below: Use external storage
            @Suppress("DEPRECATION")
            File(Environment.getExternalStorageDirectory(), "Aquazen/recordings").apply { mkdirs() }
        }
    }

    fun cleanupOldRecordings(directory: File, maxAgeMillis: Long = 24 * 60 * 60 * 1000) {
        val currentTime = System.currentTimeMillis()
        directory.listFiles()?.forEach { file ->
            if (currentTime - file.lastModified() > maxAgeMillis) {
                file.delete()
            }
        }
    }

    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in setOf(
            Environment.MEDIA_MOUNTED,
            Environment.MEDIA_MOUNTED_READ_ONLY
        )
    }

    fun getAvailableSpace(directory: File): Long {
        return directory.freeSpace
    }

    fun ensureAvailableSpace(directory: File, requiredSpace: Long): Boolean {
        return getAvailableSpace(directory) >= requiredSpace
    }
}
