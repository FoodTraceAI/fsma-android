package com.example.foodtraceai

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.time.LocalDate
import java.time.OffsetDateTime

// Define the API request body for QR code
data class SupShipArgs(
    val sscc: String,
    val tlcId: Long,
    val shipToLocationId: Long,
    val receiveDate: LocalDate,
    val receiveTime: OffsetDateTime
)

// Define the API response
data class ApiResponse(
    val success: Boolean,
    val message: String
)

// Define the login request body
data class LoginRequest(
    val email: String,
    val password: String
)

// Define the login response with a bearer token
data class LoginResponse(
    val token: String
)

interface ApiService {
    // API for user login, returns a LoginResponse with the bearer token
    @POST("/api/login")
    fun loginUser(@Body loginRequest: LoginRequest): Call<LoginResponse>

    // API for sending QR code/PTI data, requires the bearer token in the header
    @POST("/api/cte/receive/makeCteReceive")
    fun sendQRCodeData(
        @Header("Authorization") token: String,  // Bearer token added here
        @Body data: SupShipArgs
    ): Call<ApiResponse>
}