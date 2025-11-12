package com.cursosansolis.appclassws.data.remote

data class Tarea(
    val id: Int,
    val id_tema: Int,
    val titulo: String,
    val descripcion: String,
    val valor: Double,
    val fecha: String,
    val fecha_entrega: String,
    val id_clase: Int
)
