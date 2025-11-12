package com.cursosansolis.appclassws.data.remote


data class ArchivoMaterialRes(
    val id: Int,
    val id_material: Int,
    val nombre: String,
    val fecha: String,
    val url: String,
    // el PHP también devuelve estos a veces; los dejamos opcionales
    val ruta: String? = null,
    val archivo: String? = null    // base64 (no lo usas para abrir)
)