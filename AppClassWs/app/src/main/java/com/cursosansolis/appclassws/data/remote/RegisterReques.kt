package com.cursosansolis.appclassws.data.remote

data class RegisterRequest(
    val nombre: String,
    val email: String,
    val password: String   // el WS espera "password" (no "pass")
)
