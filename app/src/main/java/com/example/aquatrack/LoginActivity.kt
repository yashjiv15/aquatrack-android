package com.example.aquatrack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aquatrack.api.ApiService
import com.example.aquatrack.api.LoginRequest
import com.example.aquatrack.api.LoginResponse
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.7:8000/api/")
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
            val phone = phoneEditText.text.toString()
            val password = passwordEditText.text.toString()
            val request = LoginRequest(phone, password)
            apiService.login(request).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val loginResponse = response.body()!!
                        // Save token and user info as needed
                        if (loginResponse.user_role == "production") {
                            val intent = Intent(this@LoginActivity, ProductionActivity::class.java)
                            intent.putExtra("access_token", loginResponse.access_token)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Unauthorized role", Toast.LENGTH_SHORT).show()
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
}
