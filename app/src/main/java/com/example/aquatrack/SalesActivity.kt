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
    private var pendingOrdersLayout: LinearLayout? = null

    // Cache
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
        pendingOrdersLayout = view.findViewById(R.id.layoutPendingOrders)

        submitOrderButton?.setOnClickListener { submitOrder() }
    }

    private fun refreshOrdersTab() {
        ordersSwipe?.isRefreshing = true
        if (products.isEmpty()) fetchProducts(onComplete = { fetchPendingOrders() }) else fetchPendingOrders()
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
                pendingOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Failed to load products: ${t.localizedMessage}") }
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
                    fetchPendingOrders()
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

    private fun fetchPendingOrders() {
        api.getPendingOrders().enqueue(object : Callback<List<Order>> {
            override fun onResponse(call: Call<List<Order>>, response: Response<List<Order>>) {
                ordersSwipe?.isRefreshing = false
                if (response.isSuccessful && response.body() != null) {
                    // Sort orders by order_id in descending order (higher ID = newer = top)
                    pendingOrdersCache = response.body()!!.sortedByDescending { it.order_id }
                    pendingOrdersLayout?.let { layout ->
                        layout.removeAllViews()
                        if (pendingOrdersCache.isEmpty()) {
                            addSimpleText(layout, "No pending orders")
                        } else {
                            pendingOrdersCache.forEach { order ->
                                val cardContainer = LinearLayout(requireContext())
                                addOrderCard(cardContainer, order)
                                layout.addView(cardContainer) // Remove index 0 to maintain sorted order
                            }
                        }
                    }
                } else {
                    pendingOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Failed to load pending orders") }
                }
            }

            override fun onFailure(call: Call<List<Order>>, t: Throwable) {
                ordersSwipe?.isRefreshing = false
                pendingOrdersLayout?.let { it.removeAllViews(); addSimpleText(it, "Error: ${t.localizedMessage}") }
            }
        })
    }

    private fun addOrderCard(container: LinearLayout, order: Order) {
        val productName = order.product.product_name
        val date = formatDisplayDate(order.created_at)
        val remaining = order.order_quantity_remaining ?: order.order_quantity
        val body = "$date\n$productName | Ordered: ${order.order_quantity} | Remaining: $remaining\nCreated by: ${order.created_by ?: "Unknown"}"
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
