package com.cursosansolis.appclassws.data.remote

data class GuardarMaterialRequest(
    val id_tema: Int,
    val titulo: String,
    val descripcion: String,
    val id_clase: Int
)
