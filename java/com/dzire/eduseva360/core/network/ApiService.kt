package com.dzire.eduseva360.core.network

import com.dzire.eduseva360.core.model.LoginRequest
import com.dzire.eduseva360.core.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}
