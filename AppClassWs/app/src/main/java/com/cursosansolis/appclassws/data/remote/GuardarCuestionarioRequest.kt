package com.cursosansolis.appclassws.data.remote


data class GuardarCuestionarioRequest(
    val id_tema: Int,
    val titulo: String,
    val descripcion: String,
    val id_clase: Int
)
