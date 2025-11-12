package com.cursosansolis.appclassws.data.remote

// Request ya la tienes: data class IdTareasRequest(val id_tareas: Int)

data class HistorialTareaRes(
    val id: Int,
    val id_tareas: Int,
    val id_usuario: Int,
    val nombre: String?,
    val fecha: String?,
    val url: String,  // viene la URL de descarga
    val calificacion: String?
)
