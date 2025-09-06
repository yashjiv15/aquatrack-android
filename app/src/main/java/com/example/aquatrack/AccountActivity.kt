package com.example.aquatrack

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.aquatrack.api.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class TransactionItem(
    val type: String,
    val desc: String,
    val amount: Double,
    val date: String
)

class AccountActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var salesTotalText: TextView
    private lateinit var expensesTotalText: TextView
    private lateinit var productSpinner: Spinner
    private lateinit var saleQuantityEdit: EditText
    private lateinit var purchaseAmountEdit: EditText
    private lateinit var saleAmountEdit: EditText
    private lateinit var saleDateButton: Button
    private lateinit var saleDateText: TextView
    private lateinit var expenseDescEdit: EditText
    private lateinit var expenseAmountEdit: EditText
    private lateinit var expenseDateButton: Button
    private lateinit var expenseDateText: TextView
    private lateinit var addSaleButton: Button
    private lateinit var addExpenseButton: Button
    // private lateinit var transactionRecycler: RecyclerView
    private var products: List<Product> = emptyList()
    private var selectedSaleDate: String = ""
    private var selectedExpenseDate: String = ""
    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.7:8000/api/")
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        salesTotalText = findViewById(R.id.textSalesTotal)
        expensesTotalText = findViewById(R.id.textExpensesTotal)
        productSpinner = findViewById(R.id.spinnerProduct)
        saleQuantityEdit = findViewById(R.id.editSaleQuantity)
        purchaseAmountEdit = findViewById(R.id.editPurchaseAmount)
        saleAmountEdit = findViewById(R.id.editSaleAmount)
        saleDateButton = findViewById(R.id.buttonSelectSaleDate)
        saleDateText = findViewById(R.id.textSaleDate)
        expenseDescEdit = findViewById(R.id.editExpenseDesc)
        expenseAmountEdit = findViewById(R.id.editExpenseAmount)
        expenseDateButton = findViewById(R.id.buttonSelectExpenseDate)
        expenseDateText = findViewById(R.id.textExpenseDate)
        addSaleButton = findViewById(R.id.buttonAddSale)
        addExpenseButton = findViewById(R.id.buttonAddExpense)
        // transactionRecycler = findViewById(R.id.recyclerTransactionHistory)
        // transactionRecycler.layoutManager = LinearLayoutManager(this)

        // DatePicker for Sale
        saleDateButton.setOnClickListener {
            val now = LocalDate.now()
            val dialog = DatePickerDialog(this, { _, year, month, day ->
                val date = LocalDate.of(year, month + 1, day)
                selectedSaleDate = date.format(dateFormatter)
                saleDateText.text = selectedSaleDate
            }, now.year, now.monthValue - 1, now.dayOfMonth)
            dialog.datePicker.maxDate = System.currentTimeMillis()
            dialog.show()
        }
        // DatePicker for Expense
        expenseDateButton.setOnClickListener {
            val now = LocalDate.now()
            val dialog = DatePickerDialog(this, { _, year, month, day ->
                val date = LocalDate.of(year, month + 1, day)
                selectedExpenseDate = date.format(dateFormatter)
                expenseDateText.text = selectedExpenseDate
            }, now.year, now.monthValue - 1, now.dayOfMonth)
            dialog.datePicker.maxDate = System.currentTimeMillis()
            dialog.show()
        }

        fetchTotals()
        fetchProducts()

        addSaleButton.setOnClickListener {
            val productPosition = productSpinner.selectedItemPosition
            val saleQuantity = saleQuantityEdit.text.toString().toIntOrNull()
            val purchaseAmount = purchaseAmountEdit.text.toString().toDoubleOrNull()
            val saleAmount = saleAmountEdit.text.toString().toDoubleOrNull()
            val saleDate = if (selectedSaleDate.isNotEmpty()) selectedSaleDate else LocalDate.now().format(dateFormatter)
            if (productPosition <= 0 || saleQuantity == null || purchaseAmount == null || saleAmount == null || selectedSaleDate.isEmpty()) {
                Toast.makeText(this, "All sale fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val product = products[productPosition - 1]
            val request = CreateSaleRequest(product.product_id, saleQuantity, purchaseAmount, saleAmount, saleDate)
            apiService.createSale(request).enqueue(object : Callback<CreateSaleResponse> {
                override fun onResponse(call: Call<CreateSaleResponse>, response: Response<CreateSaleResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AccountActivity, "Sale added!", Toast.LENGTH_SHORT).show()
                        fetchTotals()
                        fetchTransactionHistory()
                    } else {
                        Toast.makeText(this@AccountActivity, "Failed to add sale", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<CreateSaleResponse>, t: Throwable) {
                    Toast.makeText(this@AccountActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
        }
        addExpenseButton.setOnClickListener {
            val desc = expenseDescEdit.text.toString()
            val amount = expenseAmountEdit.text.toString().toDoubleOrNull()
            val date = if (selectedExpenseDate.isNotEmpty()) selectedExpenseDate else LocalDate.now().format(dateFormatter)
            if (desc.isBlank() || amount == null || selectedExpenseDate.isEmpty()) {
                Toast.makeText(this, "All expense fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val request = CreateExpenseRequest(desc, amount, date)
            apiService.createExpense(request).enqueue(object : Callback<CreateExpenseResponse> {
                override fun onResponse(call: Call<CreateExpenseResponse>, response: Response<CreateExpenseResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AccountActivity, "Expense added!", Toast.LENGTH_SHORT).show()
                        fetchTotals()
                        fetchTransactionHistory()
                    } else {
                        Toast.makeText(this@AccountActivity, "Failed to add expense", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<CreateExpenseResponse>, t: Throwable) {
                    Toast.makeText(this@AccountActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
        }
        fetchTransactionHistory()
    }

    private fun fetchTotals() {
        apiService.getTotalSales().enqueue(object : Callback<TotalSalesResponse> {
            override fun onResponse(call: Call<TotalSalesResponse>, response: Response<TotalSalesResponse>) {
                salesTotalText.text = "${response.body()?.total_sales ?: 0.0}"
            }
            override fun onFailure(call: Call<TotalSalesResponse>, t: Throwable) {
                salesTotalText.text = "Null"
            }
        })
        apiService.getTotalExpenses().enqueue(object : Callback<TotalExpensesResponse> {
            override fun onResponse(call: Call<TotalExpensesResponse>, response: Response<TotalExpensesResponse>) {
                expensesTotalText.text = "${response.body()?.total_expenses ?: 0.0}"
            }
            override fun onFailure(call: Call<TotalExpensesResponse>, t: Throwable) {
                expensesTotalText.text = "Null "
            }
        })
    }

    private fun fetchProducts() {
        apiService.getProducts().enqueue(object : Callback<List<Product>> {
            override fun onResponse(call: Call<List<Product>>, response: Response<List<Product>>) {
                if (response.isSuccessful && response.body() != null) {
                    products = response.body()!!
                    val productNames = mutableListOf("Select product...")
                    productNames.addAll(products.map { "${it.product_name} (${it.quantity_type})" })
                    val adapter = ArrayAdapter(this@AccountActivity, android.R.layout.simple_spinner_item, productNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    productSpinner.adapter = adapter
                    productSpinner.setSelection(0)
                }
            }
            override fun onFailure(call: Call<List<Product>>, t: Throwable) {
                Toast.makeText(this@AccountActivity, "Failed to load products", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchTransactionHistory() {
        val transactionHistoryLayout = findViewById<LinearLayout>(R.id.layoutTransactionHistory)
        transactionHistoryLayout.removeAllViews()
        val expensesCall = apiService.getRecentExpenses()
        val salesCall = apiService.getRecentSales()
        expensesCall.enqueue(object : Callback<List<RecentExpenseItem>> {
            override fun onResponse(call: Call<List<RecentExpenseItem>>, response: Response<List<RecentExpenseItem>>) {
                val expenses = response.body() ?: emptyList()
                salesCall.enqueue(object : Callback<List<RecentSaleItem>> {
                    override fun onResponse(call: Call<List<RecentSaleItem>>, response: Response<List<RecentSaleItem>>) {
                        val sales = response.body() ?: emptyList()
                        val transactions = mutableListOf<TransactionItem>()
                        expenses.forEach {
                            transactions.add(TransactionItem(
                                type = "Expense",
                                desc = it.description,
                                amount = it.amount,
                                date = it.created_at ?: ""
                            ))
                        }
                        sales.forEach {
                            val productName = it.product?.product_name ?: "Product ID ${it.product_id}"
                            transactions.add(TransactionItem(
                                type = "Sale",
                                desc = "Sale of $productName (${it.sale_quantity} ${it.product?.quantity_type ?: ""})",
                                amount = it.product_sale_amount,
                                date = it.created_at ?: ""
                            ))
                        }
                        transactions.sortByDescending { it.date }
                        // Show latest entry first
                        for (item in transactions) {
                            val view = layoutInflater.inflate(R.layout.item_transaction, transactionHistoryLayout, false)
                            view.findViewById<TextView>(R.id.textType).text = item.type
                            view.findViewById<TextView>(R.id.textDesc).text = item.desc
                            view.findViewById<TextView>(R.id.textAmount).text = "₹${item.amount}"
                            view.findViewById<TextView>(R.id.textDate).apply {
                                text = item.date
                                setTextColor(resources.getColor(R.color.blue_dark, null))
                            }
                            transactionHistoryLayout.addView(view, 0) // Add at top for latest first
                        }
                    }
                    override fun onFailure(call: Call<List<RecentSaleItem>>, t: Throwable) {
                        val transactions = expenses.map {
                            TransactionItem("Expense", it.description, it.amount, it.created_at ?: "")
                        }
                        for (item in transactions) {
                            val view = layoutInflater.inflate(R.layout.item_transaction, transactionHistoryLayout, false)
                            view.findViewById<TextView>(R.id.textType).text = item.type
                            view.findViewById<TextView>(R.id.textDesc).text = item.desc
                            view.findViewById<TextView>(R.id.textAmount).text = "₹${item.amount}"
                            view.findViewById<TextView>(R.id.textDate).apply {
                                text = item.date
                                setTextColor(resources.getColor(R.color.blue_dark, null))
                            }
                            transactionHistoryLayout.addView(view)
                        }
                    }
                })
            }
            override fun onFailure(call: Call<List<RecentExpenseItem>>, t: Throwable) {
                transactionHistoryLayout.removeAllViews()
            }
        })
    }
}
