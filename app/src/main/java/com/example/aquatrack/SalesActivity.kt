package com.example.aquatrack

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.aquatrack.api.*
import com.google.android.material.tabs.TabLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class SalesActivity : AppCompatActivity() {
    private lateinit var api: ApiService
    private lateinit var tabLayout: TabLayout
    private lateinit var tabContainer: FrameLayout

    // Shared data
    private var products: List<Product> = emptyList()

    // Sales tab views
    private var salesSwipe: SwipeRefreshLayout? = null
    private var spinnerSaleProduct: Spinner? = null
    private var saleQtyEdit: EditText? = null
    private var salesAmountEdit: EditText? = null
    private var saleDateButton: Button? = null
    private var selectedSaleDateText: TextView? = null
    private var submitSaleButton: Button? = null
    private var salesHistoryLayout: LinearLayout? = null
    private var selectedSaleDate: String = ""

    // Orders tab views
    private var ordersSwipe: SwipeRefreshLayout? = null
    private var spinnerOrderProduct: Spinner? = null
    private var orderQtyEdit: EditText? = null
    private var submitOrderButton: Button? = null
    private var pendingOrdersLayout: LinearLayout? = null
    private var allOrdersLayout: LinearLayout? = null

    // Caches
    private var salesCache: List<RecentSaleItem> = emptyList()
    private var ordersCache: List<Order> = emptyList() // keep but sourced only from pending endpoint
    private var pendingOrdersCache: List<Order> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales)
        api = ApiClient.api
        tabLayout = findViewById(R.id.salesTabLayout)
        tabContainer = findViewById(R.id.salesTabContentContainer)
        tabLayout.addTab(tabLayout.newTab().setText("Sales"))
        tabLayout.addTab(tabLayout.newTab().setText("Orders"))
        tabLayout.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { inflateTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) { inflateTab(tab.position) }
                    fetchPendingOrders() // single refresh source
        // Load products once early
        fetchProducts(onComplete = { inflateTab(0) })
    }

    private fun inflateTab(position: Int) {
        tabContainer.removeAllViews()
        val inflater = layoutInflater
        if (position == 0) { // Sales
            inflater.inflate(R.layout.layout_sales_tab, tabContainer, true)
            bindSalesViews()
            setupSalesSpinnerIfReady()
            salesSwipe?.setOnRefreshListener { refreshSalesTab() }
            refreshSalesTab()
        } else { // Orders
            inflater.inflate(R.layout.layout_orders_tab, tabContainer, true)
            bindOrdersViews()
            setupOrdersSpinnerIfReady()
            ordersSwipe?.setOnRefreshListener { refreshOrdersTab() }
            refreshOrdersTab()
        }
    }

    private fun bindSalesViews() {
        salesSwipe = findViewById(R.id.swipeRefreshSalesTab)
        spinnerSaleProduct = findViewById(R.id.spinnerSaleProduct)
        saleQtyEdit = findViewById(R.id.editTextSaleQuantity)
        salesAmountEdit = findViewById(R.id.editTextSaleAmount)
    }

    private fun refreshOrdersTab() {
        ordersSwipe?.isRefreshing = true
        if (products.isEmpty()) fetchProducts(onComplete = { loadOrdersLists() }) else loadOrdersLists()
                    val list = response.body()!!
                    ordersCache = list // all orders view uses full list from pending endpoint
                    pendingOrdersCache = list.filter { (it.order_quantity_remaining ?: 0) > 0 }
                    // Update Pending

    private fun loadSalesLists() {
        fetchSalesList()
    }
                    // Update All Orders section (full list)
                    allOrdersLayout?.let { layout ->
                        layout.removeAllViews()
                        if (ordersCache.isEmpty()) addSimpleText(layout, "No orders") else ordersCache.forEach { addOrderCard(layout, it) }
                    }

    private fun loadOrdersLists() {
                    allOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Failed orders") }
        // Only one fetch now
        fetchPendingOrders()
    }

    private fun fetchProducts(onComplete: (() -> Unit)? = null) {
                allOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Orders error: ${t.localizedMessage}") }
        api.getProducts().enqueue(object: Callback<List<Product>> {
            override fun onResponse(call: Call<List<Product>>, response: Response<List<Product>>) {
                if (response.isSuccessful && response.body()!=null) {
                    products = response.body()!!.filter { it.is_active != false }
                    setupSalesSpinnerIfReady()
                    setupOrdersSpinnerIfReady()
                }
                onComplete?.invoke()
            }
            override fun onFailure(call: Call<List<Product>>, t: Throwable) { onComplete?.invoke() }
        })
    }

    private fun setupSalesSpinnerIfReady() {
        val spinner = spinnerSaleProduct ?: return
        val display = mutableListOf("Select product...")
        display.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, display).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setupOrdersSpinnerIfReady() {
        val spinner = spinnerOrderProduct ?: return
        val display = mutableListOf("Select product...")
        display.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, display).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun submitSale() {
        val productIndex = spinnerSaleProduct?.selectedItemPosition ?: 0
        if (productIndex <= 0) { toast("Select product"); return }
        val product = products.getOrNull(productIndex - 1) ?: run { toast("Invalid product"); return }
        val qty = saleQtyEdit?.text?.toString()?.toIntOrNull(); if (qty == null || qty <= 0) { toast("Invalid qty"); return }
        val salesAmt = salesAmountEdit?.text?.toString()?.toDoubleOrNull(); if (salesAmt == null || salesAmt < 0) { toast("Invalid amount"); return }
        if (selectedSaleDate.isBlank()) { toast("Select date"); return }
        val req = CreateSaleRequest(product_id = product.product_id, sale_quantity = qty, sales_amount = salesAmt, created_at = selectedSaleDate)
        submitSaleButton?.isEnabled = false
        api.createSale(req).enqueue(object: Callback<CreateSaleResponse> {
            override fun onResponse(call: Call<CreateSaleResponse>, response: Response<CreateSaleResponse>) {
                submitSaleButton?.isEnabled = true
                if (response.isSuccessful) {
                    toast("Sale saved")
                    saleQtyEdit?.text?.clear(); salesAmountEdit?.text?.clear(); selectedSaleDate = ""; selectedSaleDateText?.text = "No date selected"; spinnerSaleProduct?.setSelection(0)
                    fetchSalesList()
                } else toast("Save failed ${response.code()}")
            }
            override fun onFailure(call: Call<CreateSaleResponse>, t: Throwable) { submitSaleButton?.isEnabled = true; toast("Error ${t.localizedMessage}") }
        })
    }

    private fun submitOrder() {
        val productIndex = spinnerOrderProduct?.selectedItemPosition ?: 0
        if (productIndex <= 0) { toast("Select product"); return }
        val product = products.getOrNull(productIndex - 1) ?: run { toast("Invalid product"); return }
        val qty = orderQtyEdit?.text?.toString()?.toIntOrNull(); if (qty == null || qty <= 0) { toast("Invalid qty"); return }
        val req = CreateOrderRequest(product_id = product.product_id, order_quantity = qty)
        submitOrderButton?.isEnabled = false
        api.createOrder(req).enqueue(object: Callback<Order> {
            override fun onResponse(call: Call<Order>, response: Response<Order>) {
                submitOrderButton?.isEnabled = true
                if (response.isSuccessful) {
                    toast("Order saved")
                    orderQtyEdit?.text?.clear(); spinnerOrderProduct?.setSelection(0)
                    fetchPendingOrders(); fetchAllOrders()
                } else toast("Order failed ${response.code()}")
            }
            override fun onFailure(call: Call<Order>, t: Throwable) { submitOrderButton?.isEnabled = true; toast("Error ${t.localizedMessage}") }
        })
    }

    private fun fetchSalesList() {
        api.getSales().enqueue(object: Callback<List<RecentSaleItem>> {
            override fun onResponse(call: Call<List<RecentSaleItem>>, response: Response<List<RecentSaleItem>>) {
                salesSwipe?.isRefreshing = false
                if (response.isSuccessful && response.body()!=null) {
                    salesCache = response.body()!!
                    salesHistoryLayout?.let { layout ->
                        layout.removeAllViews()
                        if (salesCache.isEmpty()) addSimpleText(layout, "No sales yet") else salesCache.forEach { addSaleCard(layout, it) }
                    }
                } else {
                    salesHistoryLayout?.let { it.removeAllViews(); addSimpleText(it, "Failed to load sales") }
                }
            }
            override fun onFailure(call: Call<List<RecentSaleItem>>, t: Throwable) {
                salesSwipe?.isRefreshing = false
                salesHistoryLayout?.let { it.removeAllViews(); addSimpleText(it, "Error: ${t.localizedMessage}") }
            }
        })
    }

    private fun fetchAllOrders() {
        api.getOrders().enqueue(object: Callback<List<Order>> {
            override fun onResponse(call: Call<List<Order>>, response: Response<List<Order>>) {
                ordersSwipe?.isRefreshing = false
                if (response.isSuccessful && response.body()!=null) {
                    ordersCache = response.body()!!
                    allOrdersLayout?.let { layout ->
                        layout.removeAllViews()
                        if (ordersCache.isEmpty()) addSimpleText(layout, "No orders") else ordersCache.forEach { addOrderCard(layout, it) }
                    }
                } else {
                    allOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Failed all orders") }
                }
            }
            override fun onFailure(call: Call<List<Order>>, t: Throwable) {
                ordersSwipe?.isRefreshing = false
                allOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Orders error: ${t.localizedMessage}") }
            }
        })
    }

    private fun fetchPendingOrders() {
        api.getPendingOrders().enqueue(object: Callback<List<Order>> {
            override fun onResponse(call: Call<List<Order>>, response: Response<List<Order>>) {
                if (ordersSwipe?.isRefreshing == true) ordersSwipe?.isRefreshing = false
                if (response.isSuccessful && response.body()!=null) {
                    pendingOrdersCache = response.body()!!
                    pendingOrdersLayout?.let { layout ->
                        layout.removeAllViews()
                        if (pendingOrdersCache.isEmpty()) addSimpleText(layout, "No pending") else pendingOrdersCache.forEach { addOrderCard(layout, it) }
                    }
                } else {
                    pendingOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Failed pending") }
                }
            }
            override fun onFailure(call: Call<List<Order>>, t: Throwable) {
                if (ordersSwipe?.isRefreshing == true) ordersSwipe?.isRefreshing = false
                pendingOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Pending error: ${t.localizedMessage}") }
            }
        })
    }

    private fun addSaleCard(container: LinearLayout, item: RecentSaleItem) {
        val productName = item.product?.product_name ?: "Product ${item.product_id}"
        val date = formatDisplayDate(item.created_at)
        val body = "$date\n$productName | Qty ${item.sale_quantity} | Amount ${item.sales_amount}"
        addCard(container, body)
    }

    private fun addOrderCard(container: LinearLayout, item: Order) {
        val productName = item.product.product_name
        val qtyRemaining = item.order_quantity_remaining ?: item.order_quantity
        val date = formatDisplayDate(item.created_at)
        val body = "$date\n$productName | Qty ${item.order_quantity} | Remaining $qtyRemaining"
        addCard(container, body)
    }

    private fun addSimpleText(container: LinearLayout, msg: String) {
        val tv = TextView(this)
        tv.text = msg
        tv.setPadding(16,16,16,16)
        container.addView(tv)
    }

    private fun addCard(container: LinearLayout, body: String) {
        val card = MaterialCardView(this)
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
        tv.textSize = 14f
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

    private fun formatDisplayDate(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
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

    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}
