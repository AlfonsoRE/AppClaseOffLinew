package com.cursosansolis.appclassws.data.remote

// Debe coincidir con el JSON de consultarTemasClase.php
data class Tema(
    val id: Int,
    val titulo: String,
    val id_clase: Int
)
