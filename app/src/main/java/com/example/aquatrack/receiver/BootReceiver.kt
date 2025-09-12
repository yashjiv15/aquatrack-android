package com.example.aquatrack.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.aquatrack.R
import com.example.aquatrack.receiver.RestartJobService
import com.example.aquatrack.recording.AudioRecordingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("BootReceiver", "onReceive action=$action")

        // Only respond to BOOT_COMPLETED to avoid acting on spoofed intents
        if (action != Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Ignoring unexpected action: $action")
            return
        }

        Log.i("BootReceiver", "Device booted. Attempting to start service and schedule restarts.")
        try {
            showNotification(context)
        } catch (t: Throwable) {
            Log.e("BootReceiver", "Failed to show notification: ${t.localizedMessage}")
        }

        // Try to start the foreground service directly so it doesn't require a user login
        try {
            val svcIntent = Intent(context, AudioRecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, svcIntent)
            } else {
                context.startService(svcIntent)
            }
            Log.i("BootReceiver", "Started AudioRecordingService on boot (or requested start)")
        } catch (t: Throwable) {
            Log.e("BootReceiver", "Failed to start AudioRecordingService: ${t.localizedMessage}")
        }

        // Trigger our restart flow which will start the service and schedule periodic restarts.
        try {
            val restartAction = "com.example.aquatrack.ACTION_RESTART_SERVICE"
            val i = Intent(restartAction)
            i.setPackage(context.packageName)
            context.sendBroadcast(i)
        } catch (t: Throwable) {
            Log.e("BootReceiver", "Failed to send restart broadcast: ${t.localizedMessage}")
        }
        // Ensure JobScheduler restart job is scheduled as well
        try {
            RestartJobService.schedule(context)
        } catch (t: Throwable) {
            Log.w("BootReceiver", "Failed to schedule RestartJobService: ${t.localizedMessage}")
        }
    }

    private fun showNotification(context: Context) {
        val channelId = "aquatrack_boot"
        val channelName = "Aqua Track"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Aqua Track notification after boot"
            notificationManager.createNotificationChannel(channel)
        }

        val intentToStartService = Intent(context, NotificationActionReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intentToStartService,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_refresh_blue)
            .setContentTitle("Aqua Track")
            .setContentText("Tap to activate service.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
