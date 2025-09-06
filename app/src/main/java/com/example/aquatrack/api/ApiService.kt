package com.example.aquatrack.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// Data classes

data class LoginRequest(val phone: String, val password: String)
data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val user_id: Int,
    val user_role: String
)
data class Product(
    val product_id: Int,
    val product_name: String,
    val quantity_type_id: Int,
    val quantity_type: String,
    val created_at: String?,
    val created_by: Int?,
    val updated_at: String?,
    val updated_by: Int?,
    val is_deleted: Boolean?,
    val is_active: Boolean?
)
data class Order(
    val product_id: Int,
    val order_quantity: Int,
    val order_id: Int,
    val created_at: String?,
    val created_by: String?, // changed from Int? to String? to accept name
    val updated_at: String?,
    val updated_by: String?, // changed from Int? to String?
    val is_deleted: Boolean?,
    val product: Product,
    val order_quantity_remaining: Int? // newly added optional remaining qty
)
// Updated: production now references product directly
data class CreateProductionRequest(
    val product_id: Int,
    val produced_quantity: Double,
    val created_at: String?,
    val created_by: Int // mandatory
)
data class CreateProductionResponse(
    val product_id: Int?,
    val produced_quantity: Double?,
    val created_at: String?,
    val production_id: Int?,
    val created_by: Int?,
    val updated_at: String?,
    val updated_by: Int?,
    val is_deleted: Boolean?,
    val order: Order?, // keep for backward compat if backend returns it
    val product: Product? // also allow direct product object
)
data class StockTotalResponse(
    val product_id: Int,
    val total_stock: Int
)
data class CreateDispatchRequest(
    val order_id: Int,
    val dispatch_quantity: Int,
    val created_at: String,
    val created_by: Int // mandatory
)
data class CreateDispatchResponse(
    val dispatch_id: Int?,
    val product_id: Int?,
    val dispatch_quantity: Int?,
    val created_at: String?,
    val product: Product?
)
// For listing recent dispatches
// If backend already returns same shape as CreateDispatchResponse list, we can reuse that.

data class CreateStockRequest(
    val product_id: Int,
    val stock_quantity: Int,
    val created_at: String
)
data class CreateStockResponse(
    val stock_id: Int?,
    val product_id: Int?,
    val stock_quantity: Int?,
    val created_at: String?
)
data class DispatchTotalResponse(
    val product_id: Int,
    val total_dispatched: Int
)
data class CreateTestingRequest(
    val product_id: Int,
    val testing_quantity: Int,
    val status: String
)
data class CreateTestingResponse(
    val testing_id: Int?,
    val product_id: Int?,
    val testing_quantity: Int?,
    val status: String?,
    val created_at: String?
)
data class TestingHistoryItem(
    val testing_id: Int,
    val product_id: Int,
    val testing_quantity: Int,
    val status: String,
    val created_by: Int?,
    val created_at: String?,
    val updated_at: String?,
    val updated_by: Int?,
    val is_deleted: Boolean?,
    val product: Product
)
data class TotalSalesResponse(
    val total_sales: Double
)
data class TotalExpensesResponse(
    val total_expenses: Double
)
data class CreateSaleRequest(
    val product_id: Int,
    val sale_quantity: Int,
    val sales_amount: Double,
    val created_at: String
)
data class CreateSaleResponse(
    val sale_id: Int?,
    val product_id: Int?,
    val sale_quantity: Int?,
    val sales_amount: Double?,
    val created_at: String?,
    val product: Product? = null
)
data class CreateExpenseRequest(
    val description: String,
    val amount: Double,
    val created_at: String
)
data class CreateExpenseResponse(
    val expense_id: Int?,
    val description: String?,
    val amount: Double?,
    val created_at: String?
)
data class RecentExpenseItem(
    val expense_id: Int,
    val description: String,
    val amount: Double,
    val created_at: String?
)
data class RecentSaleItem(
    val sale_id: Int,
    val product_id: Int,
    val sale_quantity: Int,
    val sales_amount: Double,
    val created_at: String?,
    val product: Product?
)
data class CreateOrderRequest(
    val product_id: Int,
    val order_quantity: Int
)

interface ApiService {
    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @GET("orders/pending")
    fun getPendingOrders(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("include_deleted") includeDeleted: Boolean = false
    ): Call<List<Order>>

    @GET("orders")
    fun getOrders(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("include_deleted") includeDeleted: Boolean = false
    ): Call<List<Order>>

    @POST("productions")
    fun createProduction(@Body request: CreateProductionRequest): Call<CreateProductionResponse>

    @GET("productions/recent")
    fun getRecentProductions(): Call<List<CreateProductionResponse>>

    @GET("products")
    fun getProducts(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("include_deleted") includeDeleted: Boolean = false
    ): Call<List<Product>>

    @GET("stocks/total")
    fun getStockTotal(@Query("product_id") productId: Int): Call<StockTotalResponse>

    @POST("dispatches")
    fun createDispatch(@Body request: CreateDispatchRequest): Call<CreateDispatchResponse>

    @GET("dispatches/recent")
    fun getRecentDispatches(): Call<List<CreateDispatchResponse>>

    @POST("stocks")
    fun createStock(@Body request: CreateStockRequest): Call<CreateStockResponse>

    @GET("dispatches/total")
    fun getDispatchTotal(@Query("product_id") productId: Int): Call<DispatchTotalResponse>

    @POST("testing")
    fun createTesting(@Body request: CreateTestingRequest): Call<CreateTestingResponse>

    @GET("testing/recent")
    fun getRecentTesting(): Call<List<TestingHistoryItem>>

    @GET("sales/total")
    fun getTotalSales(): Call<TotalSalesResponse>

    @GET("expenses/total")
    fun getTotalExpenses(): Call<TotalExpensesResponse>

    @POST("sales")
    fun createSale(@Body request: CreateSaleRequest): Call<CreateSaleResponse>

    @POST("expenses")
    fun createExpense(@Body request: CreateExpenseRequest): Call<CreateExpenseResponse>

    @GET("expenses/recent")
    fun getRecentExpenses(): Call<List<RecentExpenseItem>>

    @GET("sales/recent")
    fun getRecentSales(): Call<List<RecentSaleItem>>

    @GET("sales")
    fun getSales(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("include_deleted") includeDeleted: Boolean = false
    ): Call<List<RecentSaleItem>>

    @POST("orders")
    fun createOrder(@Body request: CreateOrderRequest): Call<Order>
}
