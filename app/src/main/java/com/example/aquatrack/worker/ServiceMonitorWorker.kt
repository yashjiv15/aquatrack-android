package com.example.aquatrack.worker

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.aquatrack.recording.AudioRecordingService
import com.example.aquatrack.receiver.RestartJobService
import java.util.concurrent.TimeUnit

class ServiceMonitorWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        try {
            val prefs = applicationContext.getSharedPreferences("microvault", Context.MODE_PRIVATE)
            val serviceFlag = prefs.getBoolean("service_running", false)

            Log.i(TAG, "ServiceMonitorWorker running; service_running flag=$serviceFlag")

            var serviceRunning = serviceFlag
            if (!serviceRunning) {
                // As a fallback, check running services (best-effort; may be restricted on recent Android versions)
                try {
                    val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val running = am.getRunningServices(Int.MAX_VALUE)
                    serviceRunning = running.any { it.service.className.endsWith(".AudioRecordingService") || it.service.className.contains("AudioRecordingService") }
                    Log.d(TAG, "ActivityManager reports serviceRunning=$serviceRunning")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to query running services: ${t.localizedMessage}")
                }
            }

            if (!serviceRunning) {
                Log.i(TAG, "Service not running; attempting to start AudioRecordingService")
                try {
                    val svcIntent = Intent(applicationContext, AudioRecordingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(applicationContext, svcIntent)
                    } else {
                        applicationContext.startService(svcIntent)
                    }
                    // schedule job scheduler as backup
                    try { RestartJobService.schedule(applicationContext) } catch(t: Throwable){ Log.w(TAG, "Failed to schedule RestartJobService from worker: ${t.localizedMessage}") }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to start AudioRecordingService from worker: ${t.localizedMessage}")
                    return Result.retry()
                }
            }

            return Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "ServiceMonitorWorker failed: ${t.localizedMessage}")
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "ServiceMonitorWorker"
        private const val UNIQUE_NAME = "service_monitor_worker"

        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

                val request = PeriodicWorkRequestBuilder<ServiceMonitorWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Log.i(TAG, "ServiceMonitorWorker scheduled (15m)")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to schedule ServiceMonitorWorker: ${t.localizedMessage}")
            }
        }
    }
}
