package com.cursosansolis.appclassws.data.remote

data class ModificarCuestionariosContenidoRequest(
    val id: Int,            // id del registro en cuestionarios_contenido
    val pregunta: String,
    val opcion1: String?,
    val opcion2: String?,
    val opcion3: String?,
    val opcion4: String?,
    val respuesta: String
)