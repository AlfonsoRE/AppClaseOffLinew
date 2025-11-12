package com.cursosansolis.appclassws.data.remote

// Para modificarTema.php
data class ModificarTemaRequest(
    val id: Int,
    val titulo: String,
    val id_clase: Int
)
