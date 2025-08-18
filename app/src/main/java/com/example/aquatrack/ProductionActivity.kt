package com.example.aquatrack

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.aquatrack.api.ApiService
import com.example.aquatrack.api.Order
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class ProductionActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var orderSpinner: Spinner
    private lateinit var producedQuantityEditText: EditText
    private lateinit var dateButton: Button
    private lateinit var selectedDateText: TextView
    private lateinit var orderDetailsText: TextView
    private var orders: List<Order> = emptyList()
    private var selectedOrder: Order? = null
    private var selectedDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_production)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://microvaultapp.in/api/api/")
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        orderSpinner = findViewById(R.id.spinnerOrder)
        producedQuantityEditText = findViewById(R.id.editTextProducedQuantity)
        dateButton = findViewById(R.id.buttonSelectDate)
        selectedDateText = findViewById(R.id.textSelectedDate)
        orderDetailsText = findViewById(R.id.textOrderDetails)

        // Fetch orders for dropdown
        fun fetchOrders() {
            apiService.getOrders().enqueue(object : Callback<List<Order>> {
                override fun onResponse(call: Call<List<Order>>, response: Response<List<Order>>) {
                    if (response.isSuccessful && response.body() != null) {
                        orders = response.body()!!
                            .sortedByDescending { it.created_at ?: "" }
                        val orderDisplayList = mutableListOf("Select an order...")
                        orderDisplayList.addAll(orders.map {
                            val date = it.created_at ?: ""
                            "${it.product.product_name} - ${it.order_quantity} ${it.product.quantity_type} - $date"
                        })
                        orderDisplayList.add("None") // Add OTHER option
                        val adapter = ArrayAdapter(this@ProductionActivity, android.R.layout.simple_spinner_item, orderDisplayList)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        orderSpinner.adapter = adapter
                        orderSpinner.setSelection(0) // No selection by default
                        orderDetailsText.text = "Order details will appear here."
                    }
                }
                override fun onFailure(call: Call<List<Order>>, t: Throwable) {
                    Toast.makeText(this@ProductionActivity, "Failed to load orders", Toast.LENGTH_SHORT).show()
                }
            })
        }

        fetchOrders()

        val refreshButton = findViewById<ImageButton>(R.id.buttonRefreshOrders)
        refreshButton.setOnClickListener {
            fetchOrders()
        }

        orderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        // No selection
                        orderDetailsText.text = "Order details will appear here."
                        selectedOrder = null
                    }
                    orders.size + 1 -> {
                        // OTHER selected
                        orderDetailsText.text = "Other order selected. Please enter details manually."
                        selectedOrder = null
                    }
                    else -> {
                        selectedOrder = orders.getOrNull(position - 1)
                        selectedOrder?.let {
                            orderDetailsText.text = "Product: ${it.product.product_name}\nQuantity: ${it.order_quantity} ${it.product.quantity_type}"
                        }
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                orderDetailsText.text = "Order details will appear here."
                selectedOrder = null
            }
        }

        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                calendar.set(year, month, dayOfMonth)
                selectedDate = sdf.format(calendar.time)
                selectedDateText.text = "Selected Date: $selectedDate"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        val submitButton = findViewById<Button>(R.id.buttonSubmitProduction)
        submitButton.setOnClickListener {
            val selectedPosition = orderSpinner.selectedItemPosition
            val producedQuantityText = producedQuantityEditText.text.toString()
            val date = selectedDate
            if (selectedPosition <= 0) {
                Toast.makeText(this, "Please select an order.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (orderSpinner.adapter.getItem(selectedPosition) == "None") {
                Toast.makeText(this, "Manual entry for 'None' is not supported yet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val order = orders.getOrNull(selectedPosition - 1)
            if (order == null) {
                Toast.makeText(this, "Invalid order selection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (producedQuantityText.isBlank() || date.isBlank()) {
                Toast.makeText(this, "Please enter produced quantity and select a date.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val producedQuantity = producedQuantityText.toDoubleOrNull()
            if (producedQuantity == null) {
                Toast.makeText(this, "Invalid produced quantity.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val request = com.example.aquatrack.api.CreateProductionRequest(
                order_id = order.order_id,
                produced_quantity = producedQuantity,
                created_at = date
            )
            apiService.createProduction(request).enqueue(object : Callback<com.example.aquatrack.api.CreateProductionResponse> {
                override fun onResponse(call: Call<com.example.aquatrack.api.CreateProductionResponse>, response: Response<com.example.aquatrack.api.CreateProductionResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        Toast.makeText(this@ProductionActivity, "Production record created successfully!", Toast.LENGTH_LONG).show()
                        // Reset all fields after successful submit
                        orderSpinner.setSelection(0)
                        producedQuantityEditText.text.clear()
                        selectedDate = ""
                        selectedDateText.text = "No date selected"
                        orderDetailsText.text = "Order details will appear here."
                        fetchProductionHistory() // Refresh history after submit
                    } else {
                        Toast.makeText(this@ProductionActivity, "Failed to create production: ${response.code()} ${response.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<com.example.aquatrack.api.CreateProductionResponse>, t: Throwable) {
                    Toast.makeText(this@ProductionActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            })
        }

        fetchProductionHistory()
    }

    private fun fetchProductionHistory() {
        val historyLayout = findViewById<LinearLayout>(R.id.layoutProductionHistory)
        historyLayout.removeAllViews()
        apiService.getRecentProductions().enqueue(object : Callback<List<com.example.aquatrack.api.CreateProductionResponse>> {
            override fun onResponse(call: Call<List<com.example.aquatrack.api.CreateProductionResponse>>, response: Response<List<com.example.aquatrack.api.CreateProductionResponse>>) {
                if (response.isSuccessful && response.body() != null) {
                    val productions = response.body()!!
                    if (productions.isEmpty()) {
                        val emptyView = TextView(this@ProductionActivity)
                        emptyView.text = "No history available yet."
                        emptyView.setTextColor(resources.getColor(R.color.on_background, null))
                        emptyView.setPadding(16, 16, 16, 16)
                        historyLayout.addView(emptyView)
                    } else {
                        productions.forEach {
                            // Create MaterialCardView programmatically
                            val cardView = com.google.android.material.card.MaterialCardView(this@ProductionActivity)
                            val cardParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            cardParams.setMargins(0, 0, 0, 24)
                            cardView.layoutParams = cardParams
                            cardView.radius = 16f
                            cardView.cardElevation = 8f
                            cardView.setStrokeColor(resources.getColor(R.color.blue_primary, null))
                            cardView.strokeWidth = 2
                            cardView.setCardBackgroundColor(resources.getColor(R.color.white, null))

                            val innerLayout = LinearLayout(this@ProductionActivity)
                            innerLayout.orientation = LinearLayout.VERTICAL
                            innerLayout.setPadding(32, 24, 32, 24)

                            // Format date
                            val rawDate = it.created_at ?: ""
                            val formattedDate = try {
                                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                                val dateObj = parser.parse(rawDate)
                                SimpleDateFormat("dd MMM yyyy", Locale.US).format(dateObj)
                            } catch (e: Exception) {
                                rawDate
                            }

                            val dateView = TextView(this@ProductionActivity)
                            dateView.text = formattedDate
                            dateView.setTextColor(resources.getColor(R.color.blue_primary, null))
                            dateView.textSize = 16f
                            dateView.setTypeface(null, android.graphics.Typeface.BOLD)
                            dateView.setPadding(0, 0, 0, 4)

                            val productName = it.order?.product?.product_name ?: "Unknown"
                            val quantityType = it.order?.product?.quantity_type ?: ""
                            val info = "$productName | ${it.produced_quantity} $quantityType"
                            val infoView = TextView(this@ProductionActivity)
                            infoView.text = info
                            infoView.setTextColor(resources.getColor(R.color.on_background, null))
                            infoView.textSize = 18f
                            infoView.setPadding(0, 0, 0, 8)

                            innerLayout.addView(dateView)
                            innerLayout.addView(infoView)
                            cardView.addView(innerLayout)
                            historyLayout.addView(cardView)
                        }
                    }
                } else {
                    val errorView = TextView(this@ProductionActivity)
                    errorView.text = "Failed to load history."
                    errorView.setTextColor(resources.getColor(R.color.on_background, null))
                    errorView.setPadding(16, 16, 16, 16)
                    historyLayout.addView(errorView)
                }
            }
            override fun onFailure(call: Call<List<com.example.aquatrack.api.CreateProductionResponse>>, t: Throwable) {
                val errorView = TextView(this@ProductionActivity)
                errorView.text = "Network error: ${t.localizedMessage}"
                errorView.setTextColor(resources.getColor(R.color.on_background, null))
                errorView.setPadding(16, 16, 16, 16)
                historyLayout.addView(errorView)
            }
        })
    }
}
