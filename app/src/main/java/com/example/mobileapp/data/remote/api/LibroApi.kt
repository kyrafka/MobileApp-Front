package com.example.mobileapp.data.remote

import com.example.mobileapp.data.remote.model.LibroDTO
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface LibroApi {

    // Listar todos los libros
    @GET("api/v1/libros")
    suspend fun findAll(
        @Header("X-Session-Id") sessionId: String
    ): Response<List<LibroDTO>>

    // Buscar libro por ID
    @GET("api/v1/libros/{id}")
    suspend fun findById(
        @Header("X-Session-Id") sessionId: String,
        @Path("id") id: Long
    ): Response<LibroDTO>

    @POST("api/v1/libros")
    suspend fun createLibro(
        @Header("X-Session-Id") sessionId: String,
        @Body libro: LibroDTO
    ): Response<LibroDTO>

    @Multipart
    @POST("api/v1/libros/{id}/imagen")
    suspend fun uploadImagen(
        @Header("X-Session-Id") sessionId: String,
        @Path("id") libroId: Long,
        @Part file: MultipartBody.Part
    ): Response<String>
}
