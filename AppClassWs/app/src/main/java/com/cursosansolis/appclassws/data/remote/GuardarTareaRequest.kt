package com.cursosansolis.appclassws.data.remote

data class GuardarTareaRequest(
    val id_tema: Int,
    val titulo: String,
    val descripcion: String,
    val valor: Double,
    val fecha_entrega: String, // "YYYY-MM-DD HH:mm:ss"
    val id_clase: Int
)
