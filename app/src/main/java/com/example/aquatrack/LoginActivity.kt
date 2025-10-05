package com.example.aquatrack

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aquatrack.api.ApiClient
import com.example.aquatrack.api.ApiService
import com.example.aquatrack.api.LoginRequest
import com.example.aquatrack.api.LoginResponse
import com.example.aquatrack.api.RecordingApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import com.example.aquatrack.receiver.RestartJobService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings

class LoginActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var requestPermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private var allPermissionsGranted = false
    // no background polling job stored here; service & workers handle monitoring

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)
        val userRole = sharedPref.getString("user_role", null)
        val accessToken = sharedPref.getString("access_token", null)
        val storedUserId = sharedPref.getInt("user_id", -1)
        // Remove expiry check: only require is_logged_in and credentials
        if (isLoggedIn && userRole != null && accessToken != null) {
            // If user_id missing try to derive it from JWT (sub claim) and persist
            if (storedUserId == -1) {
                decodeUserIdFromJwt(accessToken)?.let { derivedId ->
                    sharedPref.edit().putInt("user_id", derivedId).apply()
                }
            }
            // User already logged in, navigate according to user_role
            when (userRole) {
                "production" -> {
                    val intent = Intent(this, ProductionActivity::class.java)
                    intent.putExtra("access_token", accessToken)
                    startActivity(intent)
                    finish()
                }
                "account" -> {
                    val intent = Intent(this, AccountActivity::class.java)
                    intent.putExtra("access_token", accessToken)
                    startActivity(intent)
                    finish()
                }
                "sales" -> {
                    val intent = Intent(this, SalesActivity::class.java)
                    intent.putExtra("access_token", accessToken)
                    startActivity(intent)
                    finish()
                }
                "admin" -> {
                    val intent = Intent(this, AdminActivity::class.java)
                    intent.putExtra("access_token", accessToken)
                    startActivity(intent)
                    finish()
                }
                else -> {
                    Toast.makeText(this, "Unauthorized role", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        setContentView(R.layout.activity_login)
        apiService = ApiClient.api

        val logoImageView = findViewById<ImageView>(R.id.logoImageView)
        // Hidden admin entry: long press on logo opens SecretSettingsActivity for administrators only
        logoImageView.setOnLongClickListener {
            startActivity(Intent(this, SecretSettingsActivity::class.java))
            true
        }
        try {
            Log.d("LoginActivity", "Trying to open aquazen_logo.png from assets...")
            val inputStream = assets.open("aquazen.png")
            val available = inputStream.available()
            Log.d("LoginActivity", "aquazen.png available bytes: $available")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                Log.d("LoginActivity", "Bitmap decoded successfully.")
                logoImageView.setImageBitmap(bitmap)
            } else {
                Log.e("LoginActivity", "Bitmap decoding failed.")
            }
            inputStream.close()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error loading aquazen_logo.png: ${e.message}")
            Toast.makeText(this, "Could not load aquazen logo", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }

        // Permission launcher for all required permissions
        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            allPermissionsGranted = result.values.all { it }
            if (!allPermissionsGranted) {
                Toast.makeText(this, "Please grant all permissions to continue", Toast.LENGTH_LONG).show()
            }
        }

        checkAndRequestAllPermissions()

        val phoneEditText = findViewById<EditText>(R.id.editTextPhone)
        val passwordEditText = findViewById<EditText>(R.id.editTextPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)

        // Set default values
        phoneEditText.setText("")
        passwordEditText.setText("")

        loginButton.setOnClickListener {
            val phone = phoneEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!allPermissionsGranted) {
                Toast.makeText(this, "Permissions pending (login still allowed)", Toast.LENGTH_SHORT).show()
            }
            loginButton.isEnabled = false
            loginButton.text = "Logging in..."
            apiService.login(LoginRequest(phone, password)).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                    if (!response.isSuccessful || response.body() == null) {
                        Toast.makeText(this@LoginActivity, "Login failed ${response.code()}", Toast.LENGTH_SHORT).show()
                        return
                    }
                    val loginResponse = response.body()!!
                    // Normalize / map deprecated roles
                    val rawRole = loginResponse.user_role.lowercase()
                    val normalizedRole = when (rawRole) {
                        "tester", "stock" -> "production" // map old roles
                        else -> rawRole
                    }
                    val allowedRoles = setOf("production", "account", "sales", "admin")
                    if (!allowedRoles.contains(normalizedRole)) {
                        Toast.makeText(this@LoginActivity, "Unauthorized role", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Save login prefs
                    val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
                    sharedPref.edit()
                        .putBoolean("is_logged_in", true)
                        .putLong("last_login_time", System.currentTimeMillis())
                        .putString("user_role", normalizedRole)
                        .putString("access_token", loginResponse.access_token)
                        .putInt("user_id", loginResponse.user_id)
                        .putString("name", loginResponse.name ?: "") // Save user's name
                        .apply()

                    // Ensure phone registration uses server-provided name and completes before starting the audio service
                    val microPrefs = getSharedPreferences("microvault", MODE_PRIVATE)
                    val existingPhoneId = microPrefs.getInt("phone_id", -1)

                    lifecycleScope.launch {
                        if (existingPhoneId == -1) {
                            // perform registration on IO dispatcher and wait
                            val recApi = RecordingApiService(this@LoginActivity)
                            val phoneNameCandidate = try { loginResponse.name ?: android.os.Build.MODEL ?: "android_device" } catch (_: Exception) { "android_device" }
                            val createdBy = loginResponse.user_id
                            val newPhoneId = withContext(Dispatchers.IO) {
                                try {
                                    recApi.registerPhone(phoneNameCandidate, createdBy)
                                } catch (t: Throwable) {
                                    Log.e("LoginActivity", "Phone registration failed: ${t.localizedMessage}")
                                    null
                                }
                            }
                            if (newPhoneId != null) {
                                microPrefs.edit().putInt("phone_id", newPhoneId).apply()
                                Log.d("LoginActivity", "Registered phone id=$newPhoneId using name='$phoneNameCandidate'")
                            } else {
                                Log.w("LoginActivity", "Phone registration returned null; microvault.phone_id remains unset")
                            }
                        } else {
                            Log.d("LoginActivity", "Phone already registered with id=$existingPhoneId")
                        }

                        // Start recording service only after attempted registration so service reads correct phone_id
                        val serviceIntent = Intent(this@LoginActivity, com.example.aquatrack.recording.AudioRecordingService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(this@LoginActivity, serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }

                        // Schedule JobScheduler job to attempt periodic restarts (resiliency)
                        try { RestartJobService.schedule(this@LoginActivity) } catch (t: Throwable) { Log.w("LoginActivity", "Failed to schedule restart job: ${t.localizedMessage}") }

                        // Schedule WorkManager monitor to ensure service is running periodically
                        try { com.example.aquatrack.worker.ServiceMonitorWorker.schedule(this@LoginActivity) } catch (t: Throwable) { Log.w("LoginActivity", "Failed to schedule ServiceMonitorWorker: ${t.localizedMessage}") }

                        // (Hidden) Admin-only actions such as prompting battery whitelist or starting kiosk mode are not executed automatically.
                        // Administrators can access those via the hidden settings (long-press app logo) which launches SecretSettingsActivity.

                        // Navigate to role screen
                        when (normalizedRole) {
                            "production" -> startActivity(Intent(this@LoginActivity, ProductionActivity::class.java))
                            "account" -> startActivity(Intent(this@LoginActivity, AccountActivity::class.java))
                            "sales" -> startActivity(Intent(this@LoginActivity, SalesActivity::class.java))
                            "admin" -> startActivity(Intent(this@LoginActivity, AdminActivity::class.java))
                        }

                        // After successful login, request permissions
                        requestExactAlarmPermissionIfNeeded()
                        requestBatteryOptimizationExemptionIfNeeded()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                    Toast.makeText(this@LoginActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun checkAndRequestAllPermissions() {
        val permissions = mutableListOf<String>()
        // Always request RECORD_AUDIO
        permissions.add(Manifest.permission.RECORD_AUDIO)
        // POST_NOTIFICATIONS is only needed for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Storage permissions only needed for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        // Only check runtime permissions
        allPermissionsGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allPermissionsGranted) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun decodeUserIdFromJwt(token: String): Int? {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return null
            val payloadSegment = parts[1]
            val decoded = android.util.Base64.decode(payloadSegment, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            val json = org.json.JSONObject(String(decoded))
            when (val subVal = json.opt("sub")) {
                is String -> subVal.toIntOrNull()
                is Int -> subVal
                is Number -> subVal.toInt()
                else -> null
            }
        } catch (e: Exception) { null }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    intent.data = Uri.parse("package:" + packageName)
                    startActivity(intent)
                    Log.i("LoginActivity", "Prompted user for SCHEDULE_EXACT_ALARM permission")
                } catch (t: Throwable) {
                    Log.e("LoginActivity", "Failed to prompt for exact alarm permission: ${t.localizedMessage}")
                }
            }
        }
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:" + packageName)
                startActivity(intent)
                Log.i("LoginActivity", "Prompted user for battery optimization exemption")
            }
        } catch (t: Throwable) {
            Log.e("LoginActivity", "Failed to prompt for battery optimization exemption: ${t.localizedMessage}")
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions in case user granted them in system settings
        checkAndRequestAllPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // Admin-only actions (battery whitelist, kiosk mode) are available in SecretSettingsActivity
}
