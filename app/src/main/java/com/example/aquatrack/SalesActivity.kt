package com.example.aquatrack

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.aquatrack.api.*
import com.google.android.material.card.MaterialCardView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class SalesActivity : AppCompatActivity() {
    private lateinit var api: ApiService

    // Shared data
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales)
        api = ApiClient.api

        bindSalesViews()
        salesSwipe?.setOnRefreshListener { refreshSalesTab() }

        // Load products and then sales
        fetchProducts(onComplete = { refreshSalesTab() })
    }

    private fun bindSalesViews() {
        salesSwipe = findViewById(R.id.swipeRefreshSalesTab)
        spinnerSaleProduct = findViewById(R.id.spinnerSaleProduct)
        saleQtyEdit = findViewById(R.id.editTextSaleQuantity)
        salesAmountEdit = findViewById(R.id.editTextSaleAmount)
        saleDateButton = findViewById(R.id.buttonSelectSaleDate)
        selectedSaleDateText = findViewById(R.id.textSelectedSaleDate)
        submitSaleButton = findViewById(R.id.buttonSubmitSale)
        salesHistoryLayout = findViewById(R.id.layoutSalesHistory)

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
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, display).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun submitSale() {
        val productIndex = spinnerSaleProduct?.selectedItemPosition ?: 0
        if (productIndex <= 0) { toast("Select product"); return }
        val product = products.getOrNull(productIndex - 1) ?: run { toast("Invalid product"); return }
        val qty = saleQtyEdit?.text?.toString()?.toIntOrNull(); if (qty == null || qty <= 0) { toast("Invalid qty"); return }
        val salesAmt = salesAmountEdit?.text?.toString()?.toDoubleOrNull(); if (salesAmt == null || salesAmt < 0) { toast("Invalid amount"); return }
        val createdAt = if (selectedSaleDate.isNotBlank()) selectedSaleDate else SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        // include created_by from stored login prefs
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
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
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
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
                                val cardContainer = LinearLayout(this@SalesActivity)
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
        val tv = TextView(this)
        tv.text = msg
        tv.setPadding(16, 16, 16, 16)
        container.addView(tv)
    }

    private fun addCard(container: LinearLayout, body: String) {
        val card = MaterialCardView(this)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, 24)
        card.layoutParams = lp
        card.radius = 16f
        card.cardElevation = 6f
        card.setCardBackgroundColor(resources.getColor(R.color.white, null))
        card.setStrokeColor(resources.getColor(R.color.blue_primary, null))
        card.strokeWidth = 2
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(32, 24, 32, 24)
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
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd")
        for (f in formats) {
            try {
                val p = SimpleDateFormat(f, Locale.US)
                val d = p.parse(raw)
                return SimpleDateFormat("dd MMM yyyy", Locale.US).format(d!!)
            } catch (_: Exception) {
            }
        }
        return raw
    }

    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}
