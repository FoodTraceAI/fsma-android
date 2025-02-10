package com.example.foodtraceai

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("tokenType") val tokenType: String,
    @SerializedName("expiresIn") val expiresIn: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("foodBusinessId") val foodBusinessId: Int,
    @SerializedName("locationId") val locationId: Int,
    @SerializedName("fsmaUserId") val fsmaUserId: Int
)
