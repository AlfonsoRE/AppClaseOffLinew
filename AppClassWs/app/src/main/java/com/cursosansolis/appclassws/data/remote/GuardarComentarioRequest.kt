package com.cursosansolis.appclassws.data.remote

data class GuardarComentarioRequest(
    val id_tarea: Int,
    val id_usuario: Int,
    val comentario: String
)