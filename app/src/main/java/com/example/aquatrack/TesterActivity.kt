package com.example.aquatrack

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.aquatrack.api.ApiService
import com.example.aquatrack.api.Product
import com.example.aquatrack.api.CreateTestingRequest
import com.example.aquatrack.api.TestingHistoryItem
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TesterActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var productSpinner: Spinner
    private lateinit var statusSpinner: Spinner
    private lateinit var quantityEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var historyLayout: LinearLayout
    private var products: List<Product> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tester)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://microvaultapp.in/api/api/")
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        productSpinner = findViewById(R.id.spinnerProduct)
        statusSpinner = findViewById(R.id.spinnerStatus)
        quantityEditText = findViewById(R.id.editTextTestingQuantity)
        submitButton = findViewById(R.id.buttonSubmitTesting)
        historyLayout = findViewById(R.id.layoutTestingHistory)

        setupStatusSpinner()
        fetchProducts()
        fetchTestingHistory()

        submitButton.setOnClickListener {
            val productPosition = productSpinner.selectedItemPosition
            if (productPosition <= 0) {
                Toast.makeText(this, "Please select a product.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val product = products[productPosition - 1]
            val status = statusSpinner.selectedItem.toString()
            val quantity = quantityEditText.text.toString().toIntOrNull()
            if (quantity == null || quantity <= 0) {
                Toast.makeText(this, "Enter a valid quantity.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val request = CreateTestingRequest(product.product_id, quantity, status)
            apiService.createTesting(request).enqueue(object : Callback<com.example.aquatrack.api.CreateTestingResponse> {
                override fun onResponse(call: Call<com.example.aquatrack.api.CreateTestingResponse>, response: Response<com.example.aquatrack.api.CreateTestingResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        Toast.makeText(this@TesterActivity, "Testing submitted!", Toast.LENGTH_SHORT).show()
                        quantityEditText.text.clear()
                        fetchTestingHistory()
                    } else {
                        Toast.makeText(this@TesterActivity, "Failed to submit testing.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<com.example.aquatrack.api.CreateTestingResponse>, t: Throwable) {
                    Toast.makeText(this@TesterActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun setupStatusSpinner() {
        val statusOptions = listOf("Select status...", "approved", "rejected")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        statusSpinner.adapter = adapter
        statusSpinner.setSelection(0)
    }

    private fun fetchProducts() {
        apiService.getProducts().enqueue(object : Callback<List<Product>> {
            override fun onResponse(call: Call<List<Product>>, response: Response<List<Product>>) {
                if (response.isSuccessful && response.body() != null) {
                    products = response.body()!!
                    val productNames = mutableListOf("Select product...")
                    productNames.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
                    val adapter = ArrayAdapter(this@TesterActivity, android.R.layout.simple_spinner_item, productNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    productSpinner.adapter = adapter
                    productSpinner.setSelection(0)
                } else {
                    Toast.makeText(this@TesterActivity, "Failed to load products", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<List<Product>>, t: Throwable) {
                Toast.makeText(this@TesterActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchTestingHistory() {
        apiService.getRecentTesting().enqueue(object : Callback<List<TestingHistoryItem>> {
            override fun onResponse(call: Call<List<TestingHistoryItem>>, response: Response<List<TestingHistoryItem>>) {
                historyLayout.removeAllViews()
                if (response.isSuccessful && response.body() != null) {
                    val history = response.body()!!
                    if (history.isEmpty()) {
                        val tv = TextView(this@TesterActivity)
                        tv.text = "No testing history found."
                        historyLayout.addView(tv)
                    } else {
                        for (item in history) {
                            val card = com.google.android.material.card.MaterialCardView(this@TesterActivity)
                            card.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, 16)
                            }
                            card.radius = 12f
                            card.cardElevation = 2f
                            card.setCardBackgroundColor(resources.getColor(R.color.white, theme))
                            card.strokeColor = resources.getColor(R.color.blue_primary, theme)
                            card.strokeWidth = 1

                            val innerLayout = LinearLayout(this@TesterActivity)
                            innerLayout.orientation = LinearLayout.VERTICAL
                            innerLayout.setPadding(18, 18, 18, 18)

                            val productText = TextView(this@TesterActivity)
                            productText.text = "${item.product.product_name} (${item.product.quantity_type})"
                            productText.setTextColor(resources.getColor(R.color.blue_primary, theme))
                            productText.textSize = 18f
                            productText.setTypeface(null, android.graphics.Typeface.BOLD)
                            innerLayout.addView(productText)

                            val detailsText = TextView(this@TesterActivity)
                            detailsText.text = "Qty: ${item.testing_quantity} | Status: ${item.status} | Date: ${item.created_at ?: item.product.created_at ?: "-"}"
                            detailsText.setTextColor(resources.getColor(R.color.blue_dark, theme))
                            detailsText.textSize = 16f
                            innerLayout.addView(detailsText)

                            card.addView(innerLayout)
                            historyLayout.addView(card)
                        }
                    }
                } else {
                    val tv = TextView(this@TesterActivity)
                    tv.text = "Failed to load history."
                    historyLayout.addView(tv)
                }
            }
            override fun onFailure(call: Call<List<TestingHistoryItem>>, t: Throwable) {
                historyLayout.removeAllViews()
                val tv = TextView(this@TesterActivity)
                tv.text = "Network error: ${t.localizedMessage}"
                historyLayout.addView(tv)
            }
        })
    }
}
