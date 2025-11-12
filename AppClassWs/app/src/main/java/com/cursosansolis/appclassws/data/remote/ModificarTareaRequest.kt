package com.cursosansolis.appclassws.data.remote
data class ModificarTareaRequest(
    val id_tema: Int,
    val titulo: String,
    val descripcion: String,
    val valor: Double,
    val fecha_entrega: String,
    val id: Int
)
