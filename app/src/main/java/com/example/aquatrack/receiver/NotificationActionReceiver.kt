package com.example.aquatrack.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.aquatrack.recording.AudioRecordingService
import com.example.aquatrack.util.NetworkManager
import com.example.aquatrack.util.NotificationHelper

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Notification action received")

        // Check network availability before starting service
        if (!NetworkManager.isNetworkAvailable(context)) {
            NotificationHelper.showNotification(
                context = context,
                title = "Network Unavailable",
                content = "Please check your internet connection and try again",
                notificationId = NETWORK_ERROR_NOTIFICATION_ID
            )
            return
        }

        // Check RECORD_AUDIO permission before starting service
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            NotificationHelper.showNotification(
                context = context,
                title = "Microphone Permission Required",
                content = "Please grant microphone permission to start recording",
                notificationId = NETWORK_ERROR_NOTIFICATION_ID
            )
            Log.e(TAG, "RECORD_AUDIO permission not granted. Service not started.")
            return
        }

        val serviceIntent = Intent(context, AudioRecordingService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
            NotificationHelper.showNotification(
                context = context,
                title = "Service Error",
                content = "Failed to start recording service",
                notificationId = SERVICE_ERROR_NOTIFICATION_ID
            )
        }

        Log.i("NotificationActionReceiver", "notification action received: ${intent.action}")
        try {
            val restartAction = "com.example.aquatrack.ACTION_RESTART_SERVICE"
            val i = Intent(restartAction)
            i.setPackage(context.packageName)
            context.sendBroadcast(i)
        } catch (t: Throwable) {
            Log.e("NotificationActionReceiver", "failed to send restart broadcast: ${t.localizedMessage}")
        }
    }

    companion object {
        private const val TAG = "NotificationReceiver"
        private const val NETWORK_ERROR_NOTIFICATION_ID = 2001
        private const val SERVICE_ERROR_NOTIFICATION_ID = 2002
    }
}
