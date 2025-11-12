package com.cursosansolis.appclassws.data.remote

data class LoginResponse(
    val success: Boolean,
    val id: String? = null,
    val rol: String? = null,
    val nombre: String? = null,
    val email: String? = null,
    val message: String? = null
)
