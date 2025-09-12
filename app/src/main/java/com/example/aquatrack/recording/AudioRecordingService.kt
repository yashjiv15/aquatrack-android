package com.example.aquatrack.recording

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.aquatrack.LoginActivity
import com.example.aquatrack.R
import com.example.aquatrack.api.RecordingApiService
import com.example.aquatrack.util.NetworkManager
import com.example.aquatrack.util.StorageManager
import com.example.aquatrack.receiver.RestartJobService
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

    // track mic permission so we do not attempt recording until granted
    private var micPermissionGranted: Boolean = false

    private fun ensureForeground(status: String) {
        val notification = getNotification(status)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, notification)
            }
        } catch (t: Throwable) {
            // Fallback to 2‑param if 3‑param not available / OEM issue
            try { startForeground(1, notification) } catch (_: Throwable) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val prefs = getSharedPreferences("microvault", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("service_running", true).apply()
        } catch (_: Exception) {}

        createNotificationChannel()
        ensureForeground("Service starting...")

        micPermissionGranted = (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        if (!micPermissionGranted) {
            Log.w(TAG, "RECORD_AUDIO permission not granted. Service will monitor but won't record until granted.")
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(1, getNotification("Mic permission required"))
        }

        // Schedule the persisted JobScheduler job so system will try to restart our job periodically
        try {
            RestartJobService.schedule(applicationContext)
            Log.i(TAG, "RestartJobService scheduled from AudioRecordingService.onCreate")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to schedule RestartJobService from service: ${t.localizedMessage}")
        }

        // Also schedule an AlarmManager broadcast as a backup restart trigger
        try {
            val action = "com.example.aquatrack.ACTION_RESTART_SERVICE"
            val pi = PendingIntent.getBroadcast(
                this,
                0,
                Intent(action).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + 60 * 1000L // 1 minute
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.i(TAG, "Scheduled AlarmManager restart from service")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to schedule AlarmManager restart from service: ${t.localizedMessage}")
        }

        val prefs = getSharedPreferences("microvault", MODE_PRIVATE)
        phoneId = prefs.getInt("phone_id", -1)
        queueDir = StorageManager.getRecordingsDirectory(applicationContext)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(1, getNotification("Syncing with server..."))
        StorageManager.cleanupOldRecordings(queueDir)

        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground("Running")

        // refresh mic permission in case user granted it while service was stopped
        micPermissionGranted = (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        if (!micPermissionGranted) {
            Log.w(TAG, "onStartCommand: mic permission not granted")
        }

        // ensure monitoring coroutine is running (service might be restarted by system)
        if (monitoringJob == null || monitoringJob?.isActive != true) {
            Log.i(TAG, "Starting monitoring job from onStartCommand")
            startMonitoring()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Schedule a broadcast that will start the service again (via RestartReceiver)
        try {
            val action = "com.example.aquatrack.ACTION_RESTART_SERVICE"
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                0,
                Intent(action).setPackage(applicationContext.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
            // Restart after 5 seconds
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pi)
            Log.i(TAG, "onTaskRemoved: scheduled restart broadcast via AlarmManager")
        } catch (t: Throwable) {
            Log.e(TAG, "onTaskRemoved: failed to schedule restart broadcast: ${t.localizedMessage}")
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            val prefs = getSharedPreferences("microvault", Context.MODE_PRIVATE)
            while (isActive) {
                // refresh phoneId so login in another activity propagates
                try {
                    val stored = prefs.getInt("phone_id", -1)
                    if (stored != phoneId) {
                        phoneId = stored
                        Log.d(TAG, "phoneId updated from prefs: $phoneId")
                    }
                } catch (_: Exception) {}

                if (NetworkManager.isNetworkAvailable(applicationContext)) {
                    if (phoneId <= 0) {
                        Log.d(TAG, "No phoneId yet; waiting for login...")
                    } else {
                        val status = try {
                            apiService.getStatus(phoneId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Status check failed", e)
                            null
                        }

                        Log.d(TAG, "Status: $status")

                        when (status) {
                            "started" -> {
                                if (!isRecording) {
                                    if (!micPermissionGranted && ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        micPermissionGranted = true
                                        Log.i(TAG, "Mic permission granted while monitoring; will start recording if server says started")
                                    }
                                    if (micPermissionGranted) startRecordingLoop() else Log.w(TAG, "Server asks to start but mic permission missing")
                                }
                            }
                            "stopped" -> if (isRecording) stopRecordingLoop()
                            else -> {
                                // unknown status — do nothing
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Network unavailable; retrying...")
                }

                delay(1000)
            }
        }
    }

    private fun startRecordingLoop() {
        recordingJob?.cancel()
        recordingJob = scope.launch {
            while (isActive) {
                // before each chunk ensure mic permission still exists
                if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Lost mic permission; stopping recording loop")
                    isRecording = false
                    break
                }

                try {
                    startNewChunk()
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to start new chunk", t)
                    delay(5000)
                }

                delay(CHUNK_DURATION_MS)
                stopAndQueue()
            }
        }
        isRecording = true
        Log.d(TAG, "Recording loop started.")
    }

    private fun stopRecordingLoop() {
        recordingJob?.cancel()
        stopAndQueue()
        recordingJob = null
        isRecording = false
        Log.d(TAG, "Recording loop stopped.")
    }

    private fun startNewChunk() {
        stopAndQueue()

        if (!::queueDir.isInitialized) {
            Log.w(TAG, "queueDir not initialized; skipping chunk")
            return
        }

        if (!StorageManager.ensureAvailableSpace(queueDir, REQUIRED_SPACE)) {
            Log.w(TAG, "Not enough storage space; attempting cleanup")
            StorageManager.cleanupOldRecordings(queueDir)
            return
        }

        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "startNewChunk called but RECORD_AUDIO not granted; skipping")
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

        Log.d(TAG, "Recording started: ${currentFile?.name}")
    }

    private fun stopAndQueue() {
        if (recorder == null) return
        try {
            recorder?.stop()
            recorder?.release()
            Log.d(TAG, "Recorded: ${currentFile?.name}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Recorder already stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        } finally {
            recorder = null
        }

        currentFile?.let { file ->
            scope.launch {
                try {
                    val success = apiService.uploadRecording(phoneId, file)
                    if (success) {
                        file.delete()
                        Log.d(TAG, "Uploaded and deleted: ${file.name}")
                    } else {
                        Log.w(TAG, "Failed to upload: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Upload failed", e)
                }
            }
        }
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        recordingJob?.cancel()
        stopAndQueue()
        try {
            val prefs = getSharedPreferences("microvault", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("service_running", false).apply()
        } catch (_: Exception) {}
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
            .setSmallIcon(R.drawable.aquazen)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
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
        private const val CHUNK_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    }
}
