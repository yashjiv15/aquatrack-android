package com.example.aquatrack.api


import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class RecordingApiService(private val context: Context) {
    private val client = OkHttpClient()
    private val serverUrl = "http://192.168.1.7:8000/api"

    private val prefs by lazy {
        context.getSharedPreferences("upload_queue", Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "ApiService"
        private const val MAX_RETRIES = 5
    }

    fun registerPhone(name: String, createdBy: Int = 0): Int? {
        val body = """{"phone_name":"$name","created_by":$createdBy}"""
            .toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$serverUrl/phones")
            .post(body)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            json.getInt("phone_id")
        }
    }

    suspend fun getStatus(phoneId: Int): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$serverUrl/phones/$phoneId/status")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = org.json.JSONObject(response.body?.string() ?: return@withContext null)
            json.getString("recording_status")
        }
    }

    suspend fun uploadRecording(phoneId: Int, file: File): Boolean {
        var attempt = 0
        var backoff = 1000L // start with 1 second

        while (attempt < MAX_RETRIES) {
            if (tryUpload(phoneId, file)) {
                removeFromFailedQueue(file.name)
                Log.i(TAG, "✅ Uploaded: ${file.name}")
                return true
            }

            attempt++
            Log.w(
                TAG,
                "⏳ Upload attempt $attempt failed for ${file.name}, retrying in ${backoff}ms"
            )
            delay(backoff)
            backoff *= 2 // exponential
        }

        Log.e(TAG, "❌ Failed to upload after $MAX_RETRIES attempts: ${file.name}")
        saveToFailedQueue(file.name)
        return false
    }

    private fun tryUpload(phoneId: Int, file: File): Boolean {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("phone_id", phoneId.toString())
            .addFormDataPart(
                "file", file.name,
                file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$serverUrl/recordings/upload")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Server response: ${response.body?.string()}")
                    true
                } else {
                    Log.w(TAG, "Server error: ${response.code} ${response.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during upload: ${e.localizedMessage}")
            false
        }
    }

    // 📝 Persistence: save file name in failed queue
    private fun saveToFailedQueue(fileName: String) {
        val set = prefs.getStringSet("failed_queue", mutableSetOf()) ?: mutableSetOf()
        set.add(fileName)
        prefs.edit().putStringSet("failed_queue", set).apply()
    }

    private fun removeFromFailedQueue(fileName: String) {
        val set = prefs.getStringSet("failed_queue", mutableSetOf()) ?: mutableSetOf()
        set.remove(fileName)
        prefs.edit().putStringSet("failed_queue", set).apply()
    }

    fun getFailedQueue(): Set<String> {
        return prefs.getStringSet("failed_queue", emptySet()) ?: emptySet()
    }
}