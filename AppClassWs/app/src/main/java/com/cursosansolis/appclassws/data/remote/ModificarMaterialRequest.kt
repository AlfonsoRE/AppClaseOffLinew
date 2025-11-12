package com.cursosansolis.appclassws.data.remote

// data/remote/Models.kt (o donde tengas los request)
data class ModificarMaterialRequest(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val id_tema: Int
)
