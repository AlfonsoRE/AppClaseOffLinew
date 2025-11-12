package com.cursosansolis.appclassws.data.remote

data class HistorialCuestionarioRes(
    val id: Int,
    val id_cuestionario: Int,
    val id_usuario: Int,
    val calificacion: String,
    val fecha: String,
    val url: String?
)