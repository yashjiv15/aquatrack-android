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
        val currentTime = System.currentTimeMillis()
        // 24 hours in milliseconds
        val twentyFourHours = 24 * 60 * 60 * 1000
        if (isLoggedIn && (currentTime - lastLoginTime) < twentyFourHours && userRole != null && accessToken != null) {
            // User already logged in within 24 hours, navigate according to user_role
            when (userRole) {
                "production" -> {
                    val intent = Intent(this, ProductionActivity::class.java)
                    intent.putExtra("access_token", accessToken)
                    startActivity(intent)
                    finish()
                }
                "stock" -> {
                    val intent = Intent(this, StockActivity::class.java)
                    intent.putExtra("access_token", accessToken)
                    startActivity(intent)
                    finish()
                }
                "tester" -> {
                    val intent = Intent(this, TesterActivity::class.java)
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

        val retrofit = Retrofit.Builder()
            .baseUrl("https://microvaultapp.in/api/api/")
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        val phoneEditText = findViewById<EditText>(R.id.editTextPhone)
        val passwordEditText = findViewById<EditText>(R.id.editTextPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)

        // Set default values
        phoneEditText.setText("9834464841")
        passwordEditText.setText("123")

        loginButton.setOnClickListener {
            if (!allPermissionsGranted) {
                Toast.makeText(this, "Please grant all permissions before logging in", Toast.LENGTH_LONG).show()
                checkAndRequestAllPermissions()
                return@setOnClickListener
            }
            val phone = phoneEditText.text.toString()
            val password = passwordEditText.text.toString()
            val request = LoginRequest(phone, password)
            apiService.login(request).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val loginResponse = response.body()!!
                        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
                        sharedPref.edit()
                            .putBoolean("is_logged_in", true)
                            .putLong("last_login_time", System.currentTimeMillis())
                            .putString("user_role", loginResponse.user_role)
                            .putString("access_token", loginResponse.access_token)
                            .apply()
                        val serviceIntent = Intent(this@LoginActivity, com.example.aquatrack.recording.AudioRecordingService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(this@LoginActivity, serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                        when (loginResponse.user_role) {
                            "production" -> {
                                val intent = Intent(this@LoginActivity, ProductionActivity::class.java)
                                intent.putExtra("access_token", loginResponse.access_token)
                                startActivity(intent)
                                finish()
                            }
                            "stock" -> {
                                val intent = Intent(this@LoginActivity, StockActivity::class.java)
                                intent.putExtra("access_token", loginResponse.access_token)
                                startActivity(intent)
                                finish()
                            }
                            "tester" -> {
                                val intent = Intent(this@LoginActivity, TesterActivity::class.java)
                                intent.putExtra("access_token", loginResponse.access_token)
                                startActivity(intent)
                                finish()
                            }
                            "account" -> {
                                val intent = Intent(this@LoginActivity, AccountActivity::class.java)
                                intent.putExtra("access_token", loginResponse.access_token)
                                startActivity(intent)
                                finish()
                            }
                            "admin" -> {
                                val intent = Intent(this@LoginActivity, AdminActivity::class.java)
                                intent.putExtra("access_token", loginResponse.access_token)
                                startActivity(intent)
                                finish()
                            }
                            else -> {
                                Toast.makeText(this@LoginActivity, "Unauthorized role", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val code = response.code()
                        Toast.makeText(this@LoginActivity, "Login failed: HTTP $code\n$errorBody", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Toast.makeText(this@LoginActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                    t.printStackTrace()
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
