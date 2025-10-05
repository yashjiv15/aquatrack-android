package com.example.aquatrack

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.aquatrack.api.*
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class SalesActivity : AppCompatActivity() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales)

        tabLayout = findViewById(R.id.tabLayoutSales)
        viewPager = findViewById(R.id.viewPagerSales)

        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = SalesTabsAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Sales"
                1 -> "Orders"
                else -> "Tab ${position + 1}"
            }
        }.attach()
    }

    inner class SalesTabsAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SalesTabFragment()
                1 -> OrdersTabFragment()
                else -> SalesTabFragment()
            }
        }
    }
}

// Sales Tab Fragment
class SalesTabFragment : Fragment() {
    private lateinit var api: ApiService
    private var products: List<Product> = emptyList()

    // Sales UI
    private var salesSwipe: SwipeRefreshLayout? = null
    private var spinnerSaleProduct: Spinner? = null
    private var saleQtyEdit: EditText? = null
    private var salesAmountEdit: EditText? = null
    private var saleDateButton: Button? = null
    private var selectedSaleDateText: TextView? = null
    private var submitSaleButton: Button? = null
    private var salesHistoryLayout: LinearLayout? = null
    private var selectedSaleDate: String = ""

    // Cache
    private var salesCache: List<RecentSaleItem> = emptyList()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        return inflater.inflate(R.layout.layout_sales_tab, container, false)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        api = ApiClient.api

        bindSalesViews(view)
        salesSwipe?.setOnRefreshListener { refreshSalesTab() }

        // Load products and then sales
        fetchProducts(onComplete = { refreshSalesTab() })
    }

    private fun bindSalesViews(view: android.view.View) {
        salesSwipe = view.findViewById(R.id.swipeRefreshSalesTab)
        spinnerSaleProduct = view.findViewById(R.id.spinnerSaleProduct)
        saleQtyEdit = view.findViewById(R.id.editTextSaleQuantity)
        salesAmountEdit = view.findViewById(R.id.editTextSaleAmount)
        saleDateButton = view.findViewById(R.id.buttonSelectSaleDate)
        selectedSaleDateText = view.findViewById(R.id.textSelectedSaleDate)
        submitSaleButton = view.findViewById(R.id.buttonSubmitSale)
        salesHistoryLayout = view.findViewById(R.id.layoutSalesHistory)

        saleDateButton?.setOnClickListener {
            pickDate { date ->
                selectedSaleDate = date
                selectedSaleDateText?.text = date
            }
        }
        submitSaleButton?.setOnClickListener { submitSale() }
    }

    private fun refreshSalesTab() {
        salesSwipe?.isRefreshing = true
        if (products.isEmpty()) fetchProducts(onComplete = { fetchSalesList() }) else fetchSalesList()
    }

    private fun fetchProducts(onComplete: (() -> Unit)? = null) {
        api.getProducts().enqueue(object : Callback<List<Product>> {
            override fun onResponse(call: Call<List<Product>>, response: Response<List<Product>>) {
                if (response.isSuccessful && response.body() != null) {
                    products = response.body()!!.filter { it.is_active != false }
                    setupSalesSpinnerIfReady()
                }
                onComplete?.invoke()
            }

            override fun onFailure(call: Call<List<Product>>, t: Throwable) {
                onComplete?.invoke()
                salesSwipe?.isRefreshing = false
                salesHistoryLayout?.let { it.removeAllViews(); addSimpleText(it, "Failed to load products: ${t.localizedMessage}") }
            }
        })
    }

    private fun setupSalesSpinnerIfReady() {
        val spinner = spinnerSaleProduct ?: return
        val display = mutableListOf("Select product...")
        display.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, display).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun submitSale() {
        val productIndex = spinnerSaleProduct?.selectedItemPosition ?: 0
        if (productIndex <= 0) { toast("Select product"); return }
        val product = products.getOrNull(productIndex - 1) ?: run { toast("Invalid product"); return }
        val qty = saleQtyEdit?.text?.toString()?.toIntOrNull(); if (qty == null || qty <= 0) { toast("Invalid qty"); return }
        val salesAmt = salesAmountEdit?.text?.toString()?.toDoubleOrNull(); if (salesAmt == null || salesAmt < 0) { toast("Invalid amount"); return }
        val createdAt = if (selectedSaleDate.isNotBlank()) selectedSaleDate else SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        // include created_by from stored login prefs
        val prefs = requireContext().getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE)
        val createdBy = prefs.getInt("user_id", -1)
        if (createdBy == -1) {
            toast("User not identified")
            return
        }

        val req = CreateSaleRequest(
            product_id = product.product_id,
            sale_quantity = qty,
            sales_amount = salesAmt,
            created_by = createdBy,
            created_at = createdAt
        )

        submitSaleButton?.isEnabled = false
        api.createSale(req).enqueue(object : Callback<CreateSaleResponse> {
            override fun onResponse(call: Call<CreateSaleResponse>, response: Response<CreateSaleResponse>) {
                submitSaleButton?.isEnabled = true
                salesSwipe?.isRefreshing = false
                if (response.isSuccessful) {
                    toast("Sale saved")
                    saleQtyEdit?.text?.clear()
                    salesAmountEdit?.text?.clear()
                    selectedSaleDate = ""
                    selectedSaleDateText?.text = "No date selected"
                    spinnerSaleProduct?.setSelection(0)
                    fetchSalesList()
                } else {
                    toast("Save failed ${response.code()}")
                }
            }

            override fun onFailure(call: Call<CreateSaleResponse>, t: Throwable) {
                submitSaleButton?.isEnabled = true
                salesSwipe?.isRefreshing = false
                toast("Error ${t.localizedMessage}")
            }
        })
    }

    private fun fetchSalesList() {
        val prefs = requireContext().getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE)
        val userId = prefs.getInt("user_id", -1)
        if (userId == -1) {
            salesSwipe?.isRefreshing = false
            salesHistoryLayout?.let { it.removeAllViews(); addSimpleText(it, "User not identified") }
            return
        }
        api.getSalesByCreatedBy(userId).enqueue(object : Callback<List<RecentSaleItem>> {
            override fun onResponse(call: Call<List<RecentSaleItem>>, response: Response<List<RecentSaleItem>>) {
                salesSwipe?.isRefreshing = false
                if (response.isSuccessful && response.body() != null) {
                    // Sort sales by sale_id in descending order (higher ID = newer = top)
                    salesCache = response.body()!!.sortedByDescending { it.sale_id }
                    salesHistoryLayout?.let { layout ->
                        layout.removeAllViews()
                        if (salesCache.isEmpty()) {
                            addSimpleText(layout, "No sales yet")
                        } else {
                            salesCache.forEach { sale ->
                                // Add each sale card at the top
                                val cardContainer = LinearLayout(requireContext())
                                addSaleCard(cardContainer, sale)
                                layout.addView(cardContainer, 0)
                            }
                        }
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

    private fun addSaleCard(container: LinearLayout, item: RecentSaleItem) {
        val productName = item.product?.product_name ?: "Product ${item.product_id}"
        val date = formatDisplayDate(item.created_at)
        val body = "$date\n$productName | Qty ${item.sale_quantity} | Amount ${item.sales_amount}"
        addCard(container, body)
    }

    private fun addSimpleText(container: LinearLayout, msg: String) {
        val tv = TextView(requireContext())
        tv.text = msg
        tv.setPadding(16, 16, 16, 16)
        container.addView(tv)
    }

    private fun addCard(container: LinearLayout, body: String) {
        val card = MaterialCardView(requireContext())
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 }
        card.cardElevation = 4f
        card.radius = 16f
        card.setContentPadding(24, 24, 24, 24)

        val tv = TextView(requireContext())
        tv.text = body
        tv.textSize = 14f
        card.addView(tv)
        container.addView(card)
    }

    private fun pickDate(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val picker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                calendar.set(year, month, dayOfMonth)
                onDateSelected(sdf.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }

    private fun formatDisplayDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "Unknown date"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val date = inputFormat.parse(dateStr)
            if (date != null) outputFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}

// Orders Tab Fragment
class OrdersTabFragment : Fragment() {
    private lateinit var api: ApiService
    private var products: List<Product> = emptyList()

    // Orders UI
    private var ordersSwipe: SwipeRefreshLayout? = null
    private var spinnerOrderProduct: Spinner? = null
    private var orderQtyEdit: EditText? = null
    private var submitOrderButton: Button? = null
    private var ordersListLayout: LinearLayout? = null

    // Cache
    private var allOrdersCache: List<Order> = emptyList()
    private var pendingOrdersCache: List<Order> = emptyList()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        return inflater.inflate(R.layout.layout_orders_tab, container, false)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        api = ApiClient.api

        bindOrdersViews(view)
        ordersSwipe?.setOnRefreshListener { refreshOrdersTab() }

        // Load products and then orders
        fetchProducts(onComplete = { refreshOrdersTab() })
    }

    private fun bindOrdersViews(view: android.view.View) {
        ordersSwipe = view.findViewById(R.id.swipeRefreshOrdersTab)
        spinnerOrderProduct = view.findViewById(R.id.spinnerOrderProduct)
        orderQtyEdit = view.findViewById(R.id.editTextOrderQuantity)
        submitOrderButton = view.findViewById(R.id.buttonSubmitOrder)
        ordersListLayout = view.findViewById(R.id.layoutPendingOrders)

        submitOrderButton?.setOnClickListener { submitOrder() }
    }

    private fun refreshOrdersTab() {
        ordersSwipe?.isRefreshing = true
        if (products.isEmpty()) fetchProducts(onComplete = { fetchAllOrders() }) else fetchAllOrders()
    }

    private fun fetchProducts(onComplete: (() -> Unit)? = null) {
        api.getProducts().enqueue(object : Callback<List<Product>> {
            override fun onResponse(call: Call<List<Product>>, response: Response<List<Product>>) {
                if (response.isSuccessful && response.body() != null) {
                    products = response.body()!!.filter { it.is_active != false }
                    setupOrdersSpinnerIfReady()
                }
                onComplete?.invoke()
            }

            override fun onFailure(call: Call<List<Product>>, t: Throwable) {
                onComplete?.invoke()
                ordersSwipe?.isRefreshing = false
                ordersListLayout?.let { it.removeAllViews(); addSimpleText(it, "Failed to load products: ${t.localizedMessage}") }
            }
        })
    }

    private fun setupOrdersSpinnerIfReady() {
        val spinner = spinnerOrderProduct ?: return
        val display = mutableListOf("Select product...")
        display.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, display).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun submitOrder() {
        val productIndex = spinnerOrderProduct?.selectedItemPosition ?: 0
        if (productIndex <= 0) { toast("Select product"); return }
        val product = products.getOrNull(productIndex - 1) ?: run { toast("Invalid product"); return }
        val qty = orderQtyEdit?.text?.toString()?.toIntOrNull(); if (qty == null || qty <= 0) { toast("Invalid qty"); return }

        // Get user ID from shared preferences
        val prefs = requireContext().getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE)
        val userId = prefs.getInt("user_id", -1)
        val userName = prefs.getString("user_name", "") ?: ""

        if (userId == -1) {
            toast("User not identified")
            return
        }

        val req = CreateOrderRequest(
            product_id = product.product_id,
            order_quantity = qty,
            created_by = userName.ifBlank { userId.toString() } // Use name if available, otherwise user ID
        )

        submitOrderButton?.isEnabled = false
        api.createOrder(req).enqueue(object : Callback<Order> {
            override fun onResponse(call: Call<Order>, response: Response<Order>) {
                submitOrderButton?.isEnabled = true
                ordersSwipe?.isRefreshing = false
                if (response.isSuccessful) {
                    toast("Order created successfully")
                    orderQtyEdit?.text?.clear()
                    spinnerOrderProduct?.setSelection(0)
                    fetchAllOrders()
                } else {
                    toast("Failed to create order ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Order>, t: Throwable) {
                submitOrderButton?.isEnabled = true
                ordersSwipe?.isRefreshing = false
                toast("Error ${t.localizedMessage}")
            }
        })
    }

    private fun fetchAllOrders() {
        // Get current user's ID from shared preferences for filtering
        val prefs = requireContext().getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE)
        val currentUserId = prefs.getInt("user_id", -1)

        if (currentUserId == -1) {
            ordersSwipe?.isRefreshing = false
            ordersListLayout?.let { it.removeAllViews(); addSimpleText(it, "User not identified") }
            return
        }

        // Fetch both all orders and pending orders to properly categorize them
        var allOrdersCompleted = false
        var pendingOrdersCompleted = false

        fun checkBothComplete() {
            if (allOrdersCompleted && pendingOrdersCompleted) {
                ordersSwipe?.isRefreshing = false
                displayOrdersList()
            }
        }

        // Fetch all orders for complete history
        api.getAllOrders().enqueue(object : Callback<List<Order>> {
            override fun onResponse(call: Call<List<Order>>, response: Response<List<Order>>) {
                if (response.isSuccessful && response.body() != null) {
                    // Filter orders by current user's ID and sort by order_id in descending order
                    allOrdersCache = response.body()!!
                        .filter { order -> order.created_by_id == currentUserId }
                        .sortedByDescending { it.order_id }
                } else {
                    allOrdersCache = emptyList()
                }
                allOrdersCompleted = true
                checkBothComplete()
            }

            override fun onFailure(call: Call<List<Order>>, t: Throwable) {
                allOrdersCache = emptyList()
                allOrdersCompleted = true
                checkBothComplete()
            }
        })

        // Fetch pending orders to get accurate remaining quantities
        api.getPendingOrders().enqueue(object : Callback<List<Order>> {
            override fun onResponse(call: Call<List<Order>>, response: Response<List<Order>>) {
                if (response.isSuccessful && response.body() != null) {
                    // Filter pending orders by current user's ID
                    pendingOrdersCache = response.body()!!
                        .filter { order -> order.created_by_id == currentUserId }
                } else {
                    pendingOrdersCache = emptyList()
                }
                pendingOrdersCompleted = true
                checkBothComplete()
            }

            override fun onFailure(call: Call<List<Order>>, t: Throwable) {
                pendingOrdersCache = emptyList()
                pendingOrdersCompleted = true
                checkBothComplete()
            }
        })
    }

    private fun displayOrdersList() {
        ordersListLayout?.let { layout ->
            layout.removeAllViews()
            if (allOrdersCache.isEmpty()) {
                addSimpleText(layout, "No orders found")
                return
            }

            // Create sets of pending order IDs for quick lookup
            val pendingOrderIds = pendingOrdersCache.map { it.order_id }.toSet()

            // Group orders by status using the pending orders lookup
            val pendingOrders = mutableListOf<Order>()
            val dispatchedOrders = mutableListOf<Order>()

            allOrdersCache.forEach { order ->
                if (pendingOrderIds.contains(order.order_id)) {
                    // Find the corresponding pending order with proper remaining quantity
                    val pendingOrder = pendingOrdersCache.find { it.order_id == order.order_id }
                    if (pendingOrder != null) {
                        pendingOrders.add(pendingOrder) // Use the pending order data with proper remaining qty
                    } else {
                        pendingOrders.add(order) // Fallback to original order
                    }
                } else {
                    dispatchedOrders.add(order)
                }
            }

            // Show Pending Orders Section first
            if (pendingOrders.isNotEmpty()) {
                addSectionHeader(layout, "Pending Orders (${pendingOrders.size})", "#FF9800") // Orange color
                pendingOrders.forEach { order ->
                    val cardContainer = LinearLayout(requireContext())
                    cardContainer.orientation = LinearLayout.VERTICAL
                    addOrderCard(cardContainer, order, "PENDING")
                    layout.addView(cardContainer)
                }
            }

            // Show Dispatched Orders Section
            if (dispatchedOrders.isNotEmpty()) {
                addSectionHeader(layout, "Dispatched Orders (${dispatchedOrders.size})", "#4CAF50") // Green color
                dispatchedOrders.forEach { order ->
                    val cardContainer = LinearLayout(requireContext())
                    cardContainer.orientation = LinearLayout.VERTICAL
                    addOrderCard(cardContainer, order, "DISPATCHED")
                    layout.addView(cardContainer)
                }
            }
        }
    }

    private fun addSectionHeader(container: LinearLayout, title: String, color: String) {
        val headerCard = MaterialCardView(requireContext())
        headerCard.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 8
            topMargin = if (container.childCount > 0) 16 else 0
        }
        headerCard.cardElevation = 2f
        headerCard.radius = 8f
        headerCard.setCardBackgroundColor(android.graphics.Color.parseColor(color))

        val headerText = TextView(requireContext())
        headerText.text = title
        headerText.textSize = 16f
        headerText.setTextColor(android.graphics.Color.WHITE)
        headerText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        headerText.setPadding(24, 16, 24, 16)

        headerCard.addView(headerText)
        container.addView(headerCard)
    }

    private fun addOrderCard(container: LinearLayout, order: Order, status: String) {
        val productName = order.product.product_name
        val date = formatDisplayDate(order.created_at)
        val remaining = order.order_quantity_remaining ?: 0
        val dispatched = if (order.order_quantity_remaining == null) {
            order.order_quantity // If null, assume fully dispatched
        } else {
            order.order_quantity - remaining
        }

        val statusColor = if (status == "PENDING") "#FF9800" else "#4CAF50" // Orange for pending, Green for dispatched
        val statusIcon = if (status == "PENDING") "⏳" else "✅"

        val body = "$statusIcon $status\n" +
                   "$date\n" +
                   "$productName\n" +
                   "Ordered: ${order.order_quantity} | Dispatched: $dispatched" +
                   if (status == "PENDING") " | Remaining: $remaining" else "" +
                   "\nCreated by: ${order.created_by ?: "Unknown"}"

        addCard(container, body, statusColor)
    }

    private fun addSimpleText(container: LinearLayout, msg: String) {
        val tv = TextView(requireContext())
        tv.text = msg
        tv.setPadding(16, 16, 16, 16)
        container.addView(tv)
    }

    private fun addCard(container: LinearLayout, body: String, statusColor: String? = null) {
        val card = MaterialCardView(requireContext())
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12 }
        card.cardElevation = 4f
        card.radius = 16f
        card.setContentPadding(24, 20, 24, 20)

        // Add status border color if provided
        if (statusColor != null) {
            card.strokeColor = android.graphics.Color.parseColor(statusColor)
            card.strokeWidth = 3
        }

        val tv = TextView(requireContext())
        tv.text = body
        tv.textSize = 14f
        tv.setLineSpacing(4f, 1f) // Add line spacing for better readability
        card.addView(tv)
        container.addView(card)
    }

    private fun formatDisplayDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "Unknown date"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val date = inputFormat.parse(dateStr)
            if (date != null) outputFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
