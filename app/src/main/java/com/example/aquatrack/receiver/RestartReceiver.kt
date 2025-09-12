package com.example.aquatrack.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.aquatrack.recording.AudioRecordingService

class RestartReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "RestartReceiver"
        private const val RESTART_INTERVAL_MS = 60 * 1000L // 1 minute
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Received restart broadcast, attempting to start AudioRecordingService")
        try {
            val svcIntent = Intent(context, AudioRecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, svcIntent)
            } else {
                context.startService(svcIntent)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start service: ${t.localizedMessage}")
        }

        // Schedule next restart
        scheduleNext(context)
    }

    private fun scheduleNext(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val action = "com.example.aquatrack.ACTION_RESTART_SERVICE"
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + RESTART_INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, "android.permission.SCHEDULE_EXACT_ALARM") == PackageManager.PERMISSION_GRANTED) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Log.d(TAG, "Scheduled next restart in ${RESTART_INTERVAL_MS}ms (exact alarm)")
                } else {
                    Log.e(TAG, "Cannot schedule exact alarm: missing SCHEDULE_EXACT_ALARM permission")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.d(TAG, "Scheduled next restart in ${RESTART_INTERVAL_MS}ms (allow while idle)")
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.d(TAG, "Scheduled next restart in ${RESTART_INTERVAL_MS}ms (exact)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to schedule next restart: ${t.localizedMessage}")
        }
    }
}
