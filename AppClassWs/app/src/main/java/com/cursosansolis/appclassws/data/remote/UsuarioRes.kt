package com.cursosansolis.appclassws.data.remote

data class UsuarioRes(
    val id: Int,
    val nombre: String,
    val email: String? = null,
    val password: String? = null,
    val rol: String? = null,
    val status: String? = null,
    val fecha_inicio: String? = null
)