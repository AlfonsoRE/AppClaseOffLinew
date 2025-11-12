package com.cursosansolis.appclassws.data.remote

data class GuardarHistorialCuestionarioRequest(
    val id_cuestionario: Int,
    val id_usuario: Int,
    val calificacion: String
)