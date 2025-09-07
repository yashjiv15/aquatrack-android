package com.example.aquatrack

import android.Manifest
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
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var requestPermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private var allPermissionsGranted = false
    private var phoneId: Int = -1
    private var statusPollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val lastLoginTime = sharedPref.getLong("last_login_time", 0L)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)
        val userRole = sharedPref.getString("user_role", null)
        val accessToken = sharedPref.getString("access_token", null)
        val storedUserId = sharedPref.getInt("user_id", -1)
        val currentTime = System.currentTimeMillis()
        // 24 hours in milliseconds
        val twentyFourHours = 24 * 60 * 60 * 1000
        if (isLoggedIn && (currentTime - lastLoginTime) < twentyFourHours && userRole != null && accessToken != null) {
            // If user_id missing try to derive it from JWT (sub claim) and persist
            if (storedUserId == -1) {
                decodeUserIdFromJwt(accessToken)?.let { derivedId ->
                    sharedPref.edit().putInt("user_id", derivedId).apply()
                }
            }
            // User already logged in within 24 hours, navigate according to user_role
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
            val phone = findViewById<EditText>(R.id.editTextPhone).text.toString().trim()
            val password = findViewById<EditText>(R.id.editTextPassword).text.toString()
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
                    val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
                    sharedPref.edit()
                        .putBoolean("is_logged_in", true)
                        .putLong("last_login_time", System.currentTimeMillis())
                        .putString("user_role", normalizedRole)
                        .putString("access_token", loginResponse.access_token)
                        .putInt("user_id", loginResponse.user_id) // store user id for created_by usage
                        .apply()
                    val serviceIntent = Intent(this@LoginActivity, com.example.aquatrack.recording.AudioRecordingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(this@LoginActivity, serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    when (normalizedRole) {
                        "production" -> startActivity(Intent(this@LoginActivity, ProductionActivity::class.java))
                        "account" -> startActivity(Intent(this@LoginActivity, AccountActivity::class.java))
                        "sales" -> startActivity(Intent(this@LoginActivity, SalesActivity::class.java))
                        "admin" -> startActivity(Intent(this@LoginActivity, AdminActivity::class.java))
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

    override fun onResume() {
        super.onResume()
        // Re-check permissions in case user granted them in system settings
        checkAndRequestAllPermissions()
    }

    override fun onDestroy() {
        statusPollingJob?.cancel()
        super.onDestroy()
    }
}
