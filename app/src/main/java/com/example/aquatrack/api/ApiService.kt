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
    val quantity_type: String
)
data class Order(
    val product_id: Int,
    val order_quantity: Int,
    val order_id: Int,
    val created_at: String?,
    val created_by: Int?,
    val updated_at: String?,
    val updated_by: Int?,
    val is_deleted: Boolean?,
    val product: Product
)
data class CreateProductionRequest(
    val order_id: Int,
    val produced_quantity: Double,
    val created_at: String?
)
data class CreateProductionResponse(
    val order_id: Int,
    val produced_quantity: Double,
    val created_at: String?,
    val production_id: Int?,
    val created_by: Int?,
    val updated_at: String?,
    val updated_by: Int?,
    val is_deleted: Boolean?,
    val order: Order?
)
data class StockTotalResponse(
    val product_id: Int,
    val total_stock: Int
)
data class CreateDispatchRequest(
    val product_id: Int,
    val dispatch_quantity: Int,
    val created_at: String
)
data class CreateDispatchResponse(
    val dispatch_id: Int?,
    val product_id: Int?,
    val dispatch_quantity: Int?,
    val created_at: String?
)
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

// API Service
interface ApiService {
    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

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

    @POST("stocks")
    fun createStock(@Body request: CreateStockRequest): Call<CreateStockResponse>
}
