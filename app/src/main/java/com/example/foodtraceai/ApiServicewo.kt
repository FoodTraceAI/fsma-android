package com.example.foodtraceai

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.time.LocalDate
import java.time.OffsetDateTime
import com.google.gson.annotations.SerializedName
import retrofit2.Callback


// Define the API request body for QR code
data class SupShipArgs(
    @SerializedName("sscc")
    val sscc: String,
    @SerializedName("tlcId")
    val tlcId: Long,
    @SerializedName("receiveLocationId") //
    val receiveLocationId: Long,
    @SerializedName("receiveDate")
    val receiveDate: LocalDate,
    @SerializedName("receiveTime")
    val receiveTime: OffsetDateTime
)

// Define the API response
data class ApiResponse(
    val success: Boolean,
    val message: String,

)

// Define the login request body
data class LoginRequest(
    val email: String,
    val password: String
)

// Define the login response with a bearer token


interface ApiService {
    // API for user login, returns a LoginResponse with the bearer token
    @POST("auth/login")
    fun loginUser(@Body request: LoginRequest): Call<LoginResponse>


    // API for sending QR code/PTI data, requires the bearer token in the header
    @POST("cte/receive/makeCteReceive")
    fun sendQRCodeData(
        @Header("Authorization") accessToken: String,  // Bearer token added here
        @Body data: SupShipArgs
    ): Call<String>
}