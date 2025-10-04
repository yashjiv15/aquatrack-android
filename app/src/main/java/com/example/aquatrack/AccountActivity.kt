package com.example.aquatrack

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.aquatrack.api.*
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AccountActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout
    private lateinit var tabContainer: FrameLayout
    // Expense tab views
    private lateinit var expenseDescEdit: EditText
    private lateinit var expenseAmountEdit: EditText
    private lateinit var expenseDateButton: Button
    private lateinit var expenseDateText: TextView
    private lateinit var addExpenseButton: Button
    private lateinit var recyclerRecentExpenses: androidx.recyclerview.widget.RecyclerView
    private lateinit var expenseAdapter: ExpenseAdapter
    private var expenses: MutableList<RecentExpenseItem> = mutableListOf()
    private var selectedExpenseDate: String = ""
    // Dispatch tab views
    private lateinit var recyclerRecentDispatches: androidx.recyclerview.widget.RecyclerView
    private lateinit var dispatchAdapter: DispatchAdapter
    private var dispatches: MutableList<CreateDispatchResponse> = mutableListOf()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.7:8000/api/")
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        tabLayout = findViewById(R.id.tabLayoutAccount)
        tabContainer = findViewById(R.id.tabContentContainerAccount)
        tabLayout.addTab(tabLayout.newTab().setText("Expenses"))
        tabLayout.addTab(tabLayout.newTab().setText("Dispatches"))
        tabLayout.addOnTabSelectedListener(object: com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) { inflateTab(tab.position) }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) { inflateTab(tab.position) }
        })
        inflateTab(0)
    }

    private fun inflateTab(position: Int) {
        tabContainer.removeAllViews()
        val inflater = layoutInflater
        if (position == 0) {
            inflater.inflate(R.layout.layout_expense_tab, tabContainer, true)
            bindExpenseTabViews()
            fetchRecentExpenses()
        } else {
            inflater.inflate(R.layout.item_view_dispatch, tabContainer, true)
            bindDispatchTabViews()
            fetchRecentDispatches()
        }
    }

    private fun bindExpenseTabViews() {
        expenseDescEdit = tabContainer.findViewById(R.id.editExpenseDesc)
        expenseAmountEdit = tabContainer.findViewById(R.id.editExpenseAmount)
        expenseDateButton = tabContainer.findViewById(R.id.buttonSelectExpenseDate)
        expenseDateText = tabContainer.findViewById(R.id.textExpenseDate)
        addExpenseButton = tabContainer.findViewById(R.id.buttonAddExpense)
        recyclerRecentExpenses = tabContainer.findViewById(R.id.recyclerRecentExpenses)
        expenseAdapter = ExpenseAdapter(expenses)
        recyclerRecentExpenses.adapter = expenseAdapter
        recyclerRecentExpenses.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        expenseDateButton.setOnClickListener {
            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH)
            val day = now.get(Calendar.DAY_OF_MONTH)
            val dialog = DatePickerDialog(this, { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d)
                selectedExpenseDate = dateFormatter.format(cal.time)
                expenseDateText.text = selectedExpenseDate
            }, year, month, day)
            dialog.datePicker.maxDate = System.currentTimeMillis()
            dialog.show()
        }
        addExpenseButton.setOnClickListener {
            val desc = expenseDescEdit.text.toString()
            val amount = expenseAmountEdit.text.toString().toDoubleOrNull()
            val date = if (selectedExpenseDate.isNotEmpty()) selectedExpenseDate else dateFormatter.format(Date())
            if (desc.isBlank() || amount == null) {
                Toast.makeText(this, "All expense fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // get logged in user id from shared prefs and include as created_by
            val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
            val createdBy = prefs.getInt("user_id", -1)
            if (createdBy == -1) {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val request = CreateExpenseRequest(desc, amount, date, createdBy)
            apiService.createExpense(request).enqueue(object : Callback<CreateExpenseResponse> {
                override fun onResponse(call: Call<CreateExpenseResponse>, response: Response<CreateExpenseResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AccountActivity, "Expense added!", Toast.LENGTH_SHORT).show()
                        // reset selected date so next addition defaults to today unless user picks
                        selectedExpenseDate = ""
                        expenseDateText.text = ""
                        // clear input fields so the form is reset for the next expense
                        expenseDescEdit.text.clear()
                        expenseAmountEdit.text.clear()
                        fetchRecentExpenses()
                    } else {
                        Toast.makeText(this@AccountActivity, "Failed to add expense", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<CreateExpenseResponse>, t: Throwable) {
                    Toast.makeText(this@AccountActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun bindDispatchTabViews() {
        recyclerRecentDispatches = tabContainer.findViewById(R.id.recyclerRecentDispatches)
        dispatchAdapter = DispatchAdapter(dispatches)
        recyclerRecentDispatches.adapter = dispatchAdapter
        recyclerRecentDispatches.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun fetchRecentExpenses() {
        apiService.getRecentExpenses().enqueue(object : Callback<List<RecentExpenseItem>> {
            override fun onResponse(call: Call<List<RecentExpenseItem>>, response: Response<List<RecentExpenseItem>>) {
                if (response.isSuccessful && response.body() != null) {
                    // use adapter's setItems for efficient updates
                    expenseAdapter.setItems(response.body()!!)
                }
            }
            override fun onFailure(call: Call<List<RecentExpenseItem>>, t: Throwable) {
                Toast.makeText(this@AccountActivity, "Failed to load expenses", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchRecentDispatches() {
        apiService.getRecentDispatches().enqueue(object : Callback<List<CreateDispatchResponse>> {
            override fun onResponse(call: Call<List<CreateDispatchResponse>>, response: Response<List<CreateDispatchResponse>>) {
                if (response.isSuccessful && response.body() != null) {
                    // use adapter's setItems for efficient updates
                    dispatchAdapter.setItems(response.body()!!)
                }
            }
            override fun onFailure(call: Call<List<CreateDispatchResponse>>, t: Throwable) {
                Toast.makeText(this@AccountActivity, "Failed to load dispatches", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
