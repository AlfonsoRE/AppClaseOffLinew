package com.cursosansolis.appclassws.data.remote

// data/remote/ComentariosModels.kt
data class ComentarioRes(
    val id: Int,
    val id_tarea: Int,
    val id_usuario: Int,
    val comentario: String,
    val fecha_comentario: String
)