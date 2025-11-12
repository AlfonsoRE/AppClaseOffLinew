package com.cursosansolis.appclassws.data.remote

object DownloadUrls {
    private const val BASE = "http://10.0.2.2/ClaseOffLine/api/"

    fun material(id: Int) = "${BASE}descargarArchivoMaterial.php?id=$id"
    fun tarea(id: Int)    = "${BASE}descargarArchivoTarea.php?id=$id"
    fun historial(id: Int)= "${BASE}descargarArchivoHistorial.php?id=$id"
}
