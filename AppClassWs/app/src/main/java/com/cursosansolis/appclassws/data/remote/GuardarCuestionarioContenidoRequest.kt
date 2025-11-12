package com.cursosansolis.appclassws.data.remote

data class GuardarCuestionarioContenidoRequest(
    val id_cuestionario: Int,
    val pregunta: String,
    val opcion1: String? = null,
    val opcion2: String? = null,
    val opcion3: String? = null,
    val opcion4: String? = null,
    val respuesta: String
)