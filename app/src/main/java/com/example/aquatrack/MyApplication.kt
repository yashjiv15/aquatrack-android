package com.example.aquatrack

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.aquatrack.receiver.RestartJobService

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean("is_logged_in", false)
            if (isLoggedIn) {
                try {
                    val svcIntent = Intent(this, com.example.aquatrack.recording.AudioRecordingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(this, svcIntent)
                    } else {
                        startService(svcIntent)
                    }
                    Log.i(TAG, "MyApplication started AudioRecordingService because user is logged in")
                } catch (t: Throwable) {
                    Log.w(TAG, "MyApplication failed to start AudioRecordingService: ${t.localizedMessage}")
                }

                try { RestartJobService.schedule(this) } catch (t: Throwable) { Log.w(TAG, "MyApplication failed to schedule RestartJobService: ${t.localizedMessage}") }
                try { com.example.aquatrack.worker.ServiceMonitorWorker.schedule(this) } catch (t: Throwable) { Log.w(TAG, "MyApplication failed to schedule ServiceMonitorWorker: ${t.localizedMessage}") }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MyApplication onCreate failed: ${t.localizedMessage}")
        }
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}

