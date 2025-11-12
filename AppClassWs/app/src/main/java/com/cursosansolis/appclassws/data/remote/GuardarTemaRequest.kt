package com.cursosansolis.appclassws.data.remote

// Para guardarTema.php (usa php://input con JSON)
data class GuardarTemaRequest(
    val titulo: String,
    val id_clase: Int
)
