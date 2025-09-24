package com.example.mobileapp.data.remote.api

import com.example.mobileapp.data.remote.model.genero.GeneroLibroDTO
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GeneroLibroApi {

    // Crea la relación libro ↔ género
    @POST("api/v1/genero-libros")
    suspend fun create(
        @Header("X-Session-Id") sessionId: String,
        @Body body: GeneroLibroDTO
    ): Response<GeneroLibroDTO>
}
