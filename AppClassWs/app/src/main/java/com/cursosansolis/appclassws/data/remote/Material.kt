package com.cursosansolis.appclassws.data.remote

data class Material(
    val id: Int,
    val id_tema: Int,
    val titulo: String,
    val descripcion: String?,
    val id_clase: Int
)
