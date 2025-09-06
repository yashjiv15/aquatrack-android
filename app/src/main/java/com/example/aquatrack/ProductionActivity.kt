package com.example.aquatrack

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.aquatrack.api.*
import com.google.android.material.tabs.TabLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class ProductionActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    // Dispatch section
    private lateinit var orderSpinner: Spinner
    private lateinit var dispatchQuantityEditText: EditText
    private lateinit var dispatchDateButton: Button
    private lateinit var selectedDispatchDateText: TextView
    private lateinit var submitDispatchButton: Button
    private lateinit var dispatchTotalText: TextView
    private lateinit var orderDetailsText: TextView
    private lateinit var dispatchHistoryLayout: LinearLayout

    // Production section
    private lateinit var productSpinner: Spinner
    private lateinit var producedQuantityEditText: EditText
    private lateinit var productionDateButton: Button
    private lateinit var selectedProductionDateText: TextView
    private lateinit var submitProductionButton: Button
    private lateinit var productionHistoryLayout: LinearLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var tabContainer: FrameLayout

    private var orders: List<Order> = emptyList()
    private var products: List<Product> = emptyList()
    private var selectedOrder: Order? = null
    private var selectedProduct: Product? = null
    private var selectedDispatchDate: String = ""
    private var selectedProductionDate: String = ""
    private var userId: Int = -1

    private var recentProductionsCache: List<CreateProductionResponse> = emptyList()
    private var recentDispatchesCache: List<CreateDispatchResponse> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_production)
        apiService = ApiClient.api
        userId = getSharedPreferences("login_prefs", MODE_PRIVATE).getInt("user_id", -1)
        tabLayout = findViewById(R.id.tabLayout)
        tabContainer = findViewById(R.id.tabContentContainer)
        tabLayout.addTab(tabLayout.newTab().setText("Dispatch"))
        tabLayout.addTab(tabLayout.newTab().setText("Production"))
        tabLayout.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { inflateTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) { // refresh on reselect
                inflateTab(tab.position)
            }
        })
        // Inflate default tab (Dispatch) and fetch its data only
        inflateTab(0)
    }

    private fun fetchDispatchData() {
        val swipe = if (::tabContainer.isInitialized) tabContainer.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh) else null
        swipe?.isRefreshing = true
        fetchOrders()
        fetchRecentDispatches()
    }

    private fun fetchProductionData() {
        val swipe = if (::tabContainer.isInitialized) tabContainer.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh) else null
        swipe?.isRefreshing = true
        fetchProducts()
        fetchRecentProductions()
    }

    private fun inflateTab(position: Int) {
        tabContainer.removeAllViews()
        val inflater = layoutInflater
        if (position == 0) {
            inflater.inflate(R.layout.layout_dispatch_tab, tabContainer, true)
            val swipe = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
            swipe.setOnRefreshListener {
                fetchDispatchData()
            }
            // re-bind dispatch views
            orderSpinner = findViewById(R.id.spinnerOrder)
            dispatchQuantityEditText = findViewById(R.id.editTextDispatchQuantity)
            dispatchDateButton = findViewById(R.id.buttonSelectDispatchDate)
            selectedDispatchDateText = findViewById(R.id.textSelectedDispatchDate)
            submitDispatchButton = findViewById(R.id.buttonSubmitDispatch)
            dispatchTotalText = findViewById(R.id.textDispatchTotal)
            orderDetailsText = findViewById(R.id.textOrderDetails)
            dispatchHistoryLayout = findViewById(R.id.layoutDispatchHistory)
            val refreshOrdersButton = findViewById<ImageButton>(R.id.buttonRefreshOrders)
            refreshOrdersButton?.setOnClickListener { fetchDispatchData() }
            setupOrderSpinner()
            dispatchDateButton.setOnClickListener { pickDate { date -> selectedDispatchDate = date; selectedDispatchDateText.text = dateDisplay(date) } }
            submitDispatchButton.setOnClickListener { submitDispatch() }
            // Fetch data for Dispatch tab
            fetchDispatchData()
            swipe.isRefreshing = false
        } else {
            inflater.inflate(R.layout.layout_production_tab, tabContainer, true)
            val swipe = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
            swipe.setOnRefreshListener {
                fetchProductionData()
            }
            productSpinner = findViewById(R.id.spinnerProduct)
            producedQuantityEditText = findViewById(R.id.editTextProducedQuantity)
            productionDateButton = findViewById(R.id.buttonSelectDate)
            selectedProductionDateText = findViewById(R.id.textSelectedDate)
            submitProductionButton = findViewById(R.id.buttonSubmitProduction)
            productionHistoryLayout = findViewById(R.id.layoutProductionHistory)
            if (products.isNotEmpty()) {
                val display = mutableListOf("Select product...")
                display.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
                productSpinner.adapter = ArrayAdapter(this@ProductionActivity, android.R.layout.simple_spinner_item, display).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            }
            if (recentProductionsCache.isNotEmpty()) {
                productionHistoryLayout.removeAllViews()
                recentProductionsCache.forEach { addProductionCard(it) }
            }
            setupProductSpinner()
            productionDateButton.setOnClickListener { pickDate { date -> selectedProductionDate = date; selectedProductionDateText.text = dateDisplay(date) } }
            submitProductionButton.setOnClickListener { submitProduction() }
            // Fetch data for Production tab
            fetchProductionData()
            swipe.isRefreshing = false
        }
    }

    private fun fetchOrders() {
        apiService.getOrders().enqueue(object : Callback<List<Order>> {
            override fun onResponse(call: Call<List<Order>>, response: Response<List<Order>>) {
                if (response.isSuccessful && response.body() != null) {
                    orders = response.body()!!.sortedByDescending { it.created_at ?: "" }
                    val display = mutableListOf("Select order...")
                    display.addAll(orders.map {
                        val createdBy = it.created_by ?: "-"
                        val remaining = it.order_quantity_remaining ?: it.order_quantity
                        "${it.product.product_name} ${it.product.quantity_type} ${remaining} ${createdBy}".trim()
                    })
                    orderSpinner.adapter = ArrayAdapter(this@ProductionActivity, android.R.layout.simple_spinner_item, display).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                } else {
                    Toast.makeText(this@ProductionActivity, "Failed orders ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<List<Order>>, t: Throwable) {
                Toast.makeText(this@ProductionActivity, "Orders error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchProducts() {
        apiService.getProducts().enqueue(object : Callback<List<Product>> {
            override fun onResponse(call: Call<List<Product>>, response: Response<List<Product>>) {
                if (response.isSuccessful && response.body() != null) {
                    products = response.body()!!.filter { it.is_active != false }
                    // Only bind to spinner if production tab currently inflated
                    if (::productSpinner.isInitialized) {
                        val display = mutableListOf("Select product...")
                        display.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
                        productSpinner.adapter = ArrayAdapter(this@ProductionActivity, android.R.layout.simple_spinner_item, display).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                    }
                } else {
                    Toast.makeText(this@ProductionActivity, "Failed products ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<List<Product>>, t: Throwable) {
                Toast.makeText(this@ProductionActivity, "Products error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupOrderSpinner() {
        orderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position <= 0) {
                    selectedOrder = null
                    orderDetailsText.text = "Order details will appear here."
                    dispatchTotalText.text = "Dispatch Total: -"
                } else {
                    selectedOrder = orders.getOrNull(position - 1)
                    selectedOrder?.let { o ->
                        val createdBy = o.created_by ?: "-"
                        val remaining = o.order_quantity_remaining ?: o.order_quantity
                        orderDetailsText.text = "${o.product.product_name} ${o.product.quantity_type} Remaining:${remaining} ${createdBy}".trim()
                        fetchDispatchTotal(o.product_id)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedOrder = null
                orderDetailsText.text = "Order details will appear here."
                dispatchTotalText.text = "Dispatch Total: -"
            }
        }
    }

    private fun setupProductSpinner() {
        productSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedProduct = if (position <= 0) null else products.getOrNull(position - 1)
            }
            override fun onNothingSelected(parent: AdapterView<*>) { selectedProduct = null }
        }
    }

    private fun ensureUserId(): Boolean {
        if (userId > 0) return true
        // Try to decode from stored access token if available
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        token?.let {
            try {
                val parts = it.split('.')
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

    private fun submitDispatch() {
        if (!ensureUserId()) return
        val order = selectedOrder
        if (order == null) {
            Toast.makeText(this, "Select order", Toast.LENGTH_SHORT).show()
            return
        }
        val qty = dispatchQuantityEditText.text.toString().toIntOrNull()
        if (qty == null || qty <= 0) {
            Toast.makeText(this, "Invalid dispatch qty", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDispatchDate.isBlank()) {
            Toast.makeText(this, "Pick dispatch date", Toast.LENGTH_SHORT).show()
            return
        }
        // Send order_id now instead of product_id
        val req = CreateDispatchRequest(order_id = order.order_id, dispatch_quantity = qty, created_at = selectedDispatchDate, created_by = userId)
        submitDispatchButton.isEnabled = false
        apiService.createDispatch(req).enqueue(object : Callback<CreateDispatchResponse> {
            override fun onResponse(call: Call<CreateDispatchResponse>, response: Response<CreateDispatchResponse>) {
                submitDispatchButton.isEnabled = true
                if (response.isSuccessful) {
                    Toast.makeText(this@ProductionActivity, "Dispatch saved", Toast.LENGTH_SHORT).show()
                    dispatchQuantityEditText.text.clear()
                    selectedDispatchDate = ""
                    selectedDispatchDateText.text = "No date selected"
                    fetchDispatchTotal(order.product_id) // still by product to update total
                    fetchRecentDispatches()
                    fetchOrders() // refresh remaining quantities
                } else {
                    Toast.makeText(this@ProductionActivity, "Dispatch fail ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<CreateDispatchResponse>, t: Throwable) {
                submitDispatchButton.isEnabled = true
                Toast.makeText(this@ProductionActivity, "Dispatch error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun submitProduction() {
        if (!ensureUserId()) return
        val product = selectedProduct
        if (product == null) {
            Toast.makeText(this, "Select product", Toast.LENGTH_SHORT).show()
            return
        }
        val qty = producedQuantityEditText.text.toString().toDoubleOrNull()
        if (qty == null || qty <= 0) {
            Toast.makeText(this, "Invalid produced qty", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedProductionDate.isBlank()) {
            Toast.makeText(this, "Pick production date", Toast.LENGTH_SHORT).show()
            return
        }
        val req = CreateProductionRequest(product_id = product.product_id, produced_quantity = qty, created_at = selectedProductionDate, created_by = userId)
        submitProductionButton.isEnabled = false
        apiService.createProduction(req).enqueue(object : Callback<CreateProductionResponse> {
            override fun onResponse(call: Call<CreateProductionResponse>, response: Response<CreateProductionResponse>) {
                submitProductionButton.isEnabled = true
                if (response.isSuccessful) {
                    Toast.makeText(this@ProductionActivity, "Production saved", Toast.LENGTH_SHORT).show()
                    producedQuantityEditText.text.clear()
                    selectedProductionDate = ""
                    selectedProductionDateText.text = "No date selected"
                    productSpinner.setSelection(0)
                    fetchRecentProductions()
                } else {
                    Toast.makeText(this@ProductionActivity, "Production fail ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<CreateProductionResponse>, t: Throwable) {
                submitProductionButton.isEnabled = true
                Toast.makeText(this@ProductionActivity, "Production error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchDispatchTotal(productId: Int) {
        apiService.getDispatchTotal(productId).enqueue(object : Callback<DispatchTotalResponse> {
            override fun onResponse(call: Call<DispatchTotalResponse>, response: Response<DispatchTotalResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    dispatchTotalText.text = "Dispatch Total: ${response.body()!!.total_dispatched}" }
                else dispatchTotalText.text = "Dispatch Total: -"
            }
            override fun onFailure(call: Call<DispatchTotalResponse>, t: Throwable) { dispatchTotalText.text = "Dispatch Total: -" }
        })
    }

    private fun fetchRecentProductions() {
        apiService.getRecentProductions().enqueue(object : Callback<List<CreateProductionResponse>> {
            override fun onResponse(call: Call<List<CreateProductionResponse>>, response: Response<List<CreateProductionResponse>>) {
                val swipe = tabContainer.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
                swipe?.isRefreshing = false
                if (response.isSuccessful && response.body() != null) {
                    recentProductionsCache = response.body()!!
                    if (::productionHistoryLayout.isInitialized) {
                        productionHistoryLayout.removeAllViews()
                        if (recentProductionsCache.isEmpty()) addSimpleText(productionHistoryLayout, "No productions yet") else recentProductionsCache.forEach { addProductionCard(it) }
                    }
                } else {
                    if (::productionHistoryLayout.isInitialized) {
                        productionHistoryLayout.removeAllViews()
                        addSimpleText(productionHistoryLayout, "Failed to load productions")
                    }
                }
            }
            override fun onFailure(call: Call<List<CreateProductionResponse>>, t: Throwable) {
                val swipe = tabContainer.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
                swipe?.isRefreshing = false
                if (::productionHistoryLayout.isInitialized) {
                    productionHistoryLayout.removeAllViews()
                    addSimpleText(productionHistoryLayout, "Network error: ${t.localizedMessage}")
                }
            }
        })
    }

    private fun fetchRecentDispatches() {
        apiService.getRecentDispatches().enqueue(object : Callback<List<CreateDispatchResponse>> {
            override fun onResponse(call: Call<List<CreateDispatchResponse>>, response: Response<List<CreateDispatchResponse>>) {
                val swipe = tabContainer.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
                swipe?.isRefreshing = false
                if (response.isSuccessful && response.body() != null) {
                    recentDispatchesCache = response.body()!!
                    if (::dispatchHistoryLayout.isInitialized) {
                        dispatchHistoryLayout.removeAllViews()
                        if (recentDispatchesCache.isEmpty()) addSimpleText(dispatchHistoryLayout, "No dispatches yet") else recentDispatchesCache.forEach { addDispatchCard(it) }
                    }
                } else {
                    if (::dispatchHistoryLayout.isInitialized) {
                        dispatchHistoryLayout.removeAllViews()
                        addSimpleText(dispatchHistoryLayout, "Failed to load dispatches")
                    }
                }
            }
            override fun onFailure(call: Call<List<CreateDispatchResponse>>, t: Throwable) {
                val swipe = tabContainer.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
                swipe?.isRefreshing = false
                if (::dispatchHistoryLayout.isInitialized) {
                    dispatchHistoryLayout.removeAllViews()
                    addSimpleText(dispatchHistoryLayout, "Network error: ${t.localizedMessage}")
                }
            }
        })
    }

    private fun addProductionCard(item: CreateProductionResponse) {
        val productName = item.product?.product_name ?: item.order?.product?.product_name ?: "Unknown"
        val qtyType = item.product?.quantity_type ?: item.order?.product?.quantity_type ?: ""
        val qty = item.produced_quantity ?: 0.0
        val date = formatDisplayDate(item.created_at)
        val text = "$date\n$productName | $qty $qtyType"
        addCard(productionHistoryLayout, text)
    }

    private fun addDispatchCard(item: CreateDispatchResponse) {
        val productName = item.product?.product_name ?: products.firstOrNull { it.product_id == item.product_id }?.product_name ?: "Product ${item.product_id}"
        val date = formatDisplayDate(item.created_at)
        val qty = item.dispatch_quantity ?: 0
        val text = "$date\n$productName | Dispatched: $qty"
        addCard(dispatchHistoryLayout, text)
    }

    private fun addSimpleText(container: LinearLayout, msg: String) {
        val tv = TextView(this)
        tv.text = msg
        tv.setPadding(16,16,16,16)
        container.addView(tv)
    }

    private fun addCard(container: LinearLayout, body: String) {
        val card = com.google.android.material.card.MaterialCardView(this)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0,0,0,24)
        card.layoutParams = lp
        card.radius = 16f
        card.cardElevation = 6f
        card.setCardBackgroundColor(resources.getColor(R.color.white, null))
        card.setStrokeColor(resources.getColor(R.color.blue_primary, null))
        card.strokeWidth = 2
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(32,24,32,24)
        val tv = TextView(this)
        tv.text = body
        tv.textSize = 16f
        inner.addView(tv)
        card.addView(inner)
        container.addView(card)
    }

    private fun pickDate(onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            onPicked(sdf.format(cal.time))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun dateDisplay(apiDate: String): String = apiDate

    private fun formatDisplayDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "";
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd")
        for (f in formats) {
            try {
                val p = SimpleDateFormat(f, Locale.US)
                val d = p.parse(raw)
                return SimpleDateFormat("dd MMM yyyy", Locale.US).format(d!!)
            } catch (_: Exception) {}
        }
        return raw
    }
}
