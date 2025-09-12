package com.example.aquatrack.receiver

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.aquatrack.recording.AudioRecordingService

@SuppressLint("SpecifyJobIdRange")
class RestartJobService : JobService() {
    companion object {
        private const val TAG = "RestartJobService"
        private const val JOB_ID = 42424
        private const val RESTART_INTERVAL_MS = 60 * 1000L // 1 minute

        fun schedule(context: Context) {
            try {
                val component = ComponentName(context, RestartJobService::class.java)
                val builder = JobInfo.Builder(JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setMinimumLatency(RESTART_INTERVAL_MS)
                    .setOverrideDeadline(RESTART_INTERVAL_MS + 10_000)

                val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                js.cancel(JOB_ID)
                val res = js.schedule(builder.build())
                Log.d(TAG, "Scheduled job (result=$res)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to schedule job: ${t.localizedMessage}")
            }
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job started — attempting to start AudioRecordingService")
        try {
            val svcIntent = Intent(applicationContext, AudioRecordingService::class.java)
            ContextCompat.startForegroundService(applicationContext, svcIntent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start service from job: ${t.localizedMessage}")
        }

        // Reschedule ourselves
        schedule(applicationContext)
        return false // no more work running
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // return true to reschedule if job was interrupted
        return true
    }
}
