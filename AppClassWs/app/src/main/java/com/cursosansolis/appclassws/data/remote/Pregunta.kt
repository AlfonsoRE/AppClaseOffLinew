package com.cursosansolis.appclassws.data.remote

data class Pregunta(
    val id: Int,
    val id_cuestionario: Int,
    val pregunta: String,
    val opcion1: String?,
    val opcion2: String?,
    val opcion3: String?,
    val opcion4: String?,
    val respuesta: String
)