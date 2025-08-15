package com.example.aquatrack

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.aquatrack.api.ApiService
import com.example.aquatrack.api.Product
import com.example.aquatrack.api.StockTotalResponse
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class StockActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var productSpinner: Spinner
    private lateinit var stockCard: LinearLayout
    private lateinit var stockText: TextView
    private var products: List<Product> = emptyList()
    private var selectedDate: String = ""
    private var selectedStockDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock)

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.7:8000/api/")
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        productSpinner = findViewById(R.id.spinnerProduct)
        stockCard = findViewById(R.id.cardStock)
        stockText = findViewById(R.id.textStockTotal)
        val dispatchQuantityEditText = findViewById<EditText>(R.id.editTextDispatchQuantity)
        val dispatchButton = findViewById<Button>(R.id.buttonSubmitDispatch)
        val stockQuantityEditText = findViewById<EditText>(R.id.editTextStockQuantity)
        val stockButton = findViewById<Button>(R.id.buttonSubmitStock)
        val dateButton = findViewById<Button>(R.id.buttonSelectDate)
        val selectedDateText = findViewById<TextView>(R.id.textSelectedDate)
        val stockDateButton = findViewById<Button>(R.id.buttonSelectStockDate)
        val selectedStockDateText = findViewById<TextView>(R.id.textSelectedStockDate)

        fetchProducts()

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

        stockDateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                calendar.set(year, month, dayOfMonth)
                selectedStockDate = sdf.format(calendar.time)
                selectedStockDateText.text = "Selected Stock Date: $selectedStockDate"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        dispatchButton.setOnClickListener {
            val position = productSpinner.selectedItemPosition
            if (position <= 0) {
                Toast.makeText(this, "Please select a product.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val product = products[position - 1]
            val quantityText = dispatchQuantityEditText.text.toString()
            val quantity = quantityText.toIntOrNull()
            if (quantity == null || quantity <= 0) {
                Toast.makeText(this, "Enter a valid dispatch quantity.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedDate.isBlank()) {
                Toast.makeText(this, "Please select a date.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val request = com.example.aquatrack.api.CreateDispatchRequest(product.product_id, quantity, selectedDate)
            apiService.createDispatch(request).enqueue(object : Callback<com.example.aquatrack.api.CreateDispatchResponse> {
                override fun onResponse(call: Call<com.example.aquatrack.api.CreateDispatchResponse>, response: Response<com.example.aquatrack.api.CreateDispatchResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        Toast.makeText(this@StockActivity, "Dispatch created successfully!", Toast.LENGTH_LONG).show()
                        dispatchQuantityEditText.text.clear()
                        fetchStockTotal(product.product_id, product.product_name)
                    } else {
                        Toast.makeText(this@StockActivity, "Failed to create dispatch.", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<com.example.aquatrack.api.CreateDispatchResponse>, t: Throwable) {
                    Toast.makeText(this@StockActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            })
        }

        stockButton.setOnClickListener {
            val position = productSpinner.selectedItemPosition
            if (position <= 0) {
                Toast.makeText(this, "Please select a product.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val product = products[position - 1]
            val quantityText = stockQuantityEditText.text.toString()
            val quantity = quantityText.toIntOrNull()
            if (quantity == null || quantity <= 0) {
                Toast.makeText(this, "Enter a valid stock quantity.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedStockDate.isBlank()) {
                Toast.makeText(this, "Please select a stock date.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val request = com.example.aquatrack.api.CreateStockRequest(product.product_id, quantity, selectedStockDate)
            apiService.createStock(request).enqueue(object : Callback<com.example.aquatrack.api.CreateStockResponse> {
                override fun onResponse(call: Call<com.example.aquatrack.api.CreateStockResponse>, response: Response<com.example.aquatrack.api.CreateStockResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        Toast.makeText(this@StockActivity, "Stock created successfully!", Toast.LENGTH_LONG).show()
                        stockQuantityEditText.text.clear()
                        fetchStockTotal(product.product_id, product.product_name)
                    } else {
                        Toast.makeText(this@StockActivity, "Failed to create stock.", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<com.example.aquatrack.api.CreateStockResponse>, t: Throwable) {
                    Toast.makeText(this@StockActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun fetchProducts() {
        apiService.getProducts().enqueue(object : Callback<List<Product>> {
            override fun onResponse(call: Call<List<Product>>, response: Response<List<Product>>) {
                if (response.isSuccessful && response.body() != null) {
                    products = response.body()!!
                    val productNames = mutableListOf("Select a product...")
                    productNames.addAll(products.map { it.product_name })
                    val adapter = ArrayAdapter(this@StockActivity, android.R.layout.simple_spinner_item, productNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    productSpinner.adapter = adapter
                    productSpinner.setSelection(0)
                    productSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                            if (position == 0) {
                                stockCard.visibility = View.GONE
                            } else {
                                val product = products[position - 1]
                                fetchStockTotal(product.product_id, product.product_name)
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>) {
                            stockCard.visibility = View.GONE
                        }
                    }
                } else {
                    Toast.makeText(this@StockActivity, "Failed to load products", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<List<Product>>, t: Throwable) {
                Toast.makeText(this@StockActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchStockTotal(productId: Int, productName: String) {
        apiService.getStockTotal(productId).enqueue(object : Callback<StockTotalResponse> {
            override fun onResponse(call: Call<StockTotalResponse>, response: Response<StockTotalResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val stock = response.body()!!
                    stockText.text = "$productName\nTotal Stock: ${stock.total_stock}"
                    stockCard.visibility = View.VISIBLE
                } else {
                    stockText.text = "Failed to load stock."
                    stockCard.visibility = View.VISIBLE
                }
            }
            override fun onFailure(call: Call<StockTotalResponse>, t: Throwable) {
                stockText.text = "Network error: ${t.localizedMessage}"
                stockCard.visibility = View.VISIBLE
            }
        })
    }
}
