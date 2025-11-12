package com.cursosansolis.appclassws.data.remote

import com.google.gson.annotations.SerializedName

data class AddClassRequest(
    val nombre: String,
    val materia: String,
    val descripcion: String,
    val codigo: String,
    @SerializedName("id_usuario") val idUsuario: String
)