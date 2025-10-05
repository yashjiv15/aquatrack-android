package com.example.aquatrack

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.aquatrack.api.ApiService
import com.example.aquatrack.api.Product
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
    private lateinit var dispatchCard: LinearLayout
    private lateinit var dispatchText: TextView
    private var products: List<Product> = emptyList()
    private var selectedDate: String = ""
    private var selectedStockDate: String = ""
    private var userId: Int = -1

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock)
        userId = getSharedPreferences("login_prefs", MODE_PRIVATE).getInt("user_id", -1)

        val toolbar = findViewById<Toolbar>(R.id.toolbarStock)
        setSupportActionBar(toolbar)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://microvaultapp.in/api/api/")
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        productSpinner = findViewById(R.id.spinnerProduct)
        stockCard = findViewById(R.id.cardStock)
        stockText = findViewById(R.id.textStockTotal)
        dispatchCard = findViewById(R.id.cardDispatch)
        dispatchText = findViewById(R.id.textDispatchTotal)
        val dispatchQuantityEditText = findViewById<EditText>(R.id.editTextDispatchQuantity)
        val dispatchButton = findViewById<Button>(R.id.buttonSubmitDispatch)
        val stockQuantityEditText = findViewById<EditText>(R.id.editTextStockQuantity)
        val stockButton = findViewById<Button>(R.id.buttonSubmitStock)
        val dateButton = findViewById<Button>(R.id.buttonSelectDate)
        val selectedDateText = findViewById<TextView>(R.id.textSelectedDate)
        val stockDateButton = findViewById<Button>(R.id.buttonSelectStockDate)
        val selectedStockDateText = findViewById<TextView>(R.id.textSelectedStockDate)

        // Hide dispatch and stock sections initially
        val dispatchSection = findViewById<View>(R.id.buttonSelectDate).parent as View
        val stockSection = findViewById<View>(R.id.buttonSelectStockDate).parent as View
        dispatchSection.visibility = View.GONE
        stockSection.visibility = View.GONE

        fetchProducts()

        productSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    stockCard.visibility = View.GONE
                    dispatchCard.visibility = View.GONE
                    dispatchSection.visibility = View.GONE
                    stockSection.visibility = View.GONE
                } else {
                    val product = products[position - 1]
                    fetchStockAndDispatch(product.product_id, product.product_name)
                    dispatchSection.visibility = View.VISIBLE
                    stockSection.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                stockCard.visibility = View.GONE
                dispatchCard.visibility = View.GONE
                dispatchSection.visibility = View.GONE
                stockSection.visibility = View.GONE
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
            datePicker.datePicker.maxDate = System.currentTimeMillis()
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
            datePicker.datePicker.maxDate = System.currentTimeMillis()
            datePicker.show()
        }

        dispatchButton.setOnClickListener {
            if (!ensureUserId()) return@setOnClickListener
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
            val request = com.example.aquatrack.api.CreateDispatchRequest(order_id = product.product_id, dispatch_quantity = quantity, created_at = selectedDate, created_by = userId)
            apiService.createDispatch(request).enqueue(object : Callback<com.example.aquatrack.api.CreateDispatchResponse> {
                override fun onResponse(call: Call<com.example.aquatrack.api.CreateDispatchResponse>, response: Response<com.example.aquatrack.api.CreateDispatchResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        Toast.makeText(this@StockActivity, "Dispatch created successfully!", Toast.LENGTH_LONG).show()
                        dispatchQuantityEditText.text.clear()
                        fetchStockAndDispatch(product.product_id, product.product_name)
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
                        fetchStockAndDispatch(product.product_id, product.product_name)
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_stock, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh) {
            val position = productSpinner.selectedItemPosition
            if (position > 0) {
                val product = products[position - 1]
                fetchStockAndDispatch(product.product_id, product.product_name)
            } else {
                Toast.makeText(this, "Please select a product to refresh.", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fetchProducts() {
        apiService.getProducts().enqueue(object : Callback<List<Product>> {
            override fun onResponse(call: Call<List<Product>>, response: Response<List<Product>>) {
                if (response.isSuccessful && response.body() != null) {
                    products = response.body()!!
                    val productNames = mutableListOf("Select a product...")
                    productNames.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
                    val adapter = ArrayAdapter(this@StockActivity, android.R.layout.simple_spinner_item, productNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    productSpinner.adapter = adapter
                    productSpinner.setSelection(0)
                } else {
                    Toast.makeText(this@StockActivity, "Failed to load products", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<List<Product>>, t: Throwable) {
                Toast.makeText(this@StockActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchStockAndDispatch(productId: Int, productName: String) {
        apiService.getStockTotal(productId).enqueue(object : Callback<com.example.aquatrack.api.StockTotalResponse> {
            override fun onResponse(call: Call<com.example.aquatrack.api.StockTotalResponse>, response: Response<com.example.aquatrack.api.StockTotalResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val stock = response.body()!!
                    stockText.text = "$productName\nTotal Stock: ${stock.total_stock}"
                    stockCard.visibility = View.VISIBLE
                } else {
                    stockText.text = "Failed to load stock."
                    stockCard.visibility = View.VISIBLE
                }
            }
            override fun onFailure(call: Call<com.example.aquatrack.api.StockTotalResponse>, t: Throwable) {
                stockText.text = "Network error: ${t.localizedMessage}"
                stockCard.visibility = View.VISIBLE
            }
        })
        apiService.getDispatchTotal(productId).enqueue(object : Callback<com.example.aquatrack.api.DispatchTotalResponse> {
            override fun onResponse(call: Call<com.example.aquatrack.api.DispatchTotalResponse>, response: Response<com.example.aquatrack.api.DispatchTotalResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val dispatch = response.body()!!
                    dispatchText.text = "$productName\nTotal Dispatched: ${dispatch.total_dispatched}"
                    dispatchCard.visibility = View.VISIBLE
                } else {
                    dispatchText.text = "Failed to load dispatch count."
                    dispatchCard.visibility = View.VISIBLE
                }
            }
            override fun onFailure(call: Call<com.example.aquatrack.api.DispatchTotalResponse>, t: Throwable) {
                dispatchText.text = "Network error: ${t.localizedMessage}"
                dispatchCard.visibility = View.VISIBLE
            }
        })
    }

    private fun ensureUserId(): Boolean {
        if (userId > 0) return true
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        if (token != null) {
            try {
                val parts = token.split('.')
                if (parts.size >= 2) {
                    val decoded = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                    val json = org.json.JSONObject(String(decoded))
                    val sub = json.opt("sub")
                    val derived = when (sub) {
                        is String -> sub.toIntOrNull()
                        is Int -> sub
                        is Number -> sub.toInt()
                        else -> null
                    }
                    if (derived != null && derived > 0) {
                        userId = derived
                        prefs.edit().putInt("user_id", derived).apply()
                    }
                }
            } catch (_: Exception) {}
        }
        if (userId <= 0) {
            Toast.makeText(this, "Missing user id. Please re-login.", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }
}
