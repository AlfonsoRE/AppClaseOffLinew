package com.cursosansolis.appclassws.data.remote


import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.JsonArray


import okhttp3.MultipartBody
import okhttp3.RequestBody

import retrofit2.http.Multipart

import retrofit2.http.Part



interface ApiService {

    // LOGIN (form-url-encoded)
    @FormUrlEncoded
    @POST("loginApp.php")
    fun login(
        @Field("email") email: String,
        @Field("pass") pass: String
    ): Call<LoginResponse>

    // REGISTRO (JSON)
    @Headers("Content-Type: application/json")
    @POST("guardarUsuario.php")
    fun register(
        @Body body: RegisterRequest
    ): Call<ResponseBody>

    // CREAR CLASE (JSON)
    @Headers("Content-Type: application/json")
    @POST("guardarClases.php") // o addClase.php si así se llama en tu servidor
    fun addClass(
        @Body body: AddClassRequest
    ): Call<ResponseBody>

    // UNIRSE A CLASE (JSON)
    @Headers("Content-Type: application/json")
    @POST("guardarClasesEstudiantes.php") // o joinClase.php si así se llama
    fun joinClass(
        @Body body: JoinClassRequest
    ): Call<ResponseBody>

    @Headers("Content-Type: application/json")
    @POST("consultarClaseIdUsuario.php")
    fun clasesImpartidas(
        @Body body: IdUsuarioRequest
    ): Call<JsonArray>

    @Headers("Content-Type: application/json")
    @POST("consultarClasesEstudianteIdEstudiante.php")
    fun clasesInscritas(
        @Body body: IdUsuarioRequest   // usamos el mismo campo id_usuario
    ): Call<JsonArray>

    @Headers("Content-Type: application/json")
    @POST("consultarAlumnosPorClase.php")
    fun alumnosPorClase(
        @Body body: IdClaseRequest
    ): Call<JsonArray>

    @Headers("Content-Type: application/json")
    @POST("eliminarClasesEstudiantes.php")
    fun expulsarAlumno(
        @Body body: IdRelacionRequest
    ): Call<ResponseBody>


    // 2) clase por id (usa id)
    // data/remote/ApiService.kt
    @Headers("Content-Type: application/json")
    @POST("consultarClase.php")
    fun clasePorId(@Body body: IdIntRequest): Call<com.google.gson.JsonArray>

    // Todas las clases (por si no usas la de arriba)
    @Headers("Content-Type: application/json")
    @POST("consultarClases.php")
    fun clasesTodas(): Call<JsonArray>

    @Headers("Content-Type: application/json")
    @POST("consultarUsuario.php")
    fun usuarioPorId(@Body body: IdIntRequest): Call<com.google.gson.JsonArray>


    // 1) Consultar anuncios de una clase
    @Headers("Content-Type: application/json")
    @POST("consultarAnunciosClase.php")
    fun consultarAnunciosClase(
        @Body body: IdClaseRequest        // { "id_clase": "..." }
    ): Call<JsonArray>

    // 2) Guardar un anuncio (texto)
    @Headers("Content-Type: application/json")
    @POST("guardarAnuncio.php")
    fun guardarAnuncio(
        @Body body: GuardarAnuncioRequest // { "id_clase": "...", "id_usuario": "...", "contenido": "..." }
    ): Call<ResponseBody>

    // 3) Guardar un enlace en un anuncio
    @Headers("Content-Type: application/json")
    @POST("guardarEnlacesAnuncios.php")   // (si tu archivo se llama así)
    fun guardarEnlaceAnuncio(
        @Body body: GuardarEnlaceRequest  // { "id_anuncios": "...", "enlace": "https://..." }
    ): Call<ResponseBody>

    // ApiService.kt
    @Multipart
    @POST("guardarArchivoAnuncio.php")
    fun subirArchivoAnuncio(
        @Part archivo: MultipartBody.Part,     // nombre del part: "archivo"
        @Part("json") json: RequestBody        // nombre del part: "json"
    ): Call<ResponseBody>


// 5) Consultar enlaces por anuncio
    @Headers("Content-Type: application/json")
    @POST("consultarEnlacesAnuncioIdAnuncios.php")  // (o el que corresponda)
    fun consultarEnlacesPorAnuncio(
        @Body body: IdAnuncioRequest
    ): Call<JsonArray>



    @Headers("Content-Type: application/json")
    @POST("consultarArchivoAnunciosPorAnuncio.php")
    fun consultarArchivosPorAnuncio(
        @Body body: IdAnuncioSimpleRequest
    ): Call<JsonArray>



    @Headers("Content-Type: application/json")
    @POST("consultarAnunciosArchivosEnlaces.php")
    fun consultarAnunciosArchivosEnlaces(
        @Body body: IdClaseRequest         // { "id_clase": "..." }
    ): Call<JsonArray>


    // ---- ELIMINAR ----
    @Headers("Content-Type: application/json")
    @POST("eliminarAnuncio.php")
    fun eliminarAnuncio(@Body body: IdOnlyRequest): Call<ResponseBody>

    @Headers("Content-Type: application/json")
    @POST("eliminarArchivoAnuncio.php")
    fun eliminarArchivoAnuncio(@Body body: IdOnlyRequest): Call<ResponseBody>

    @Headers("Content-Type: application/json")
    @POST("eliminarEnlacesAnuncios.php")
    fun eliminarEnlaceAnuncio(@Body body: IdOnlyRequest): Call<ResponseBody>

    // -------- TEMAS --------
    @POST("guardarTema.php")
    suspend fun guardarTema(@Body body: GuardarTemaRequest): okhttp3.ResponseBody
// El PHP devuelve "Registro exitoso" (texto plano)

    @POST("consultarTemasClase.php")
    suspend fun consultarTemasClase(@Body body: IdClaseRequest): List<Tema>
// Devuelve JSON con { id, titulo, id_clase }

    @POST("modificarTema.php")
    suspend fun modificarTema(@Body body: ModificarTemaRequest): okhttp3.ResponseBody
// Devuelve "Registro modificado"

    @POST("eliminarTema.php")
    suspend fun eliminarTema(@Body body: IdOnlyRequest): okhttp3.ResponseBody
// Devuelve "Registro eliminado"


    // -------- TAREAS --------
    @POST("guardarTarea.php")
    suspend fun guardarTarea(@Body body: GuardarTareaRequest): ResponseBody
// PHP responde con el id insertado (texto plano)

    @POST("guardarEnlaceTarea.php")
    suspend fun guardarEnlaceTarea(@Body body: GuardarEnlaceTareaRequest): ResponseBody
// Lo usaremos después; responde JSON con "mensaje"

    @Multipart
    @POST("guardarArchivoTarea.php")
    suspend fun guardarArchivoTarea(
        @Part("json") json: RequestBody,           // {"id_tareas": 123}
        @Part archivo: MultipartBody.Part          // el file elegido
    ): ResponseBody
// Lo usaremos después; responde JSON {status,message}


    @POST("consultarTareasClases.php")
    suspend fun consultarTareasClases(@Body body: IdClaseRequest): List<Tarea>


    @POST("consultarEnlaceTareaPorTarea.php")
    suspend fun consultarEnlaceTareaPorTarea(@Body body: IdTareasRequest): List<EnlaceTarea>

    @POST("consultarArchivoTareasporTarea.php")
    suspend fun consultarArchivoTareasporTarea(
        @Body body: IdTareasRequest
    ): List<ArchivoTarea>

    @POST("modificarTarea.php")
    suspend fun modificarTarea(@Body body: ModificarTareaRequest): ResponseBody

    @POST("eliminarArchivoTareas.php")
    suspend fun eliminarArchivoTareas(@Body body: IdIntRequest): ResponseBody

    @POST("eliminarEnlaceTarea.php")
    suspend fun eliminarEnlaceTarea(@Body body: IdIntRequest): ResponseBody

    @POST("eliminarTarea.php")
    suspend fun eliminarTarea(@Body body: IdIntRequest): okhttp3.ResponseBody




    //Material
    @POST("guardarMaterial.php")               // <-- asumiendo mismo patrón que tarea
    suspend fun guardarMaterial(
        @Body body: GuardarMaterialRequest
    ): ResponseBody   // el PHP devuelve el id como texto

    @POST("guardarEnlaceMaterial.php")
    suspend fun guardarEnlaceMaterial(
        @Body body: GuardarEnlaceMaterialRequest
    ): ResponseBody

    @Multipart
    @POST("guardarArchivosMaterial.php")
    suspend fun guardarArchivoMaterial(
        @Part("json") json: RequestBody,
        @Part archivo: MultipartBody.Part
    ): ResponseBody



    // —— LISTADOS GLOBALES (sin body) ——
    @POST("consultarMaterialIdTema.php")
    suspend fun consultarMaterialIdTema(@Body body: IdTemaRequest): List<MaterialRes>

    @POST("consultarEnlaceMaterialPorMaterial.php")
    suspend fun consultarEnlaceMaterialPorMaterial(@Body body: IdMaterialRequest): List<EnlaceMaterialRes>

    @POST("consultarArchivosMaterialIdMaterial.php")
    suspend fun consultarArchivosMaterialIdMaterial(@Body body: IdMaterialRequest): List<ArchivoMaterialRes>




//Cuestionario


    @POST("guardarCuestionario.php")
    suspend fun guardarCuestionario(
        @Body body: GuardarCuestionarioRequest
    ): GuardarCuestionarioResponse

    @POST("guardarCuestionariosContenido.php")
    suspend fun guardarCuestionariosContenido(
        @Body body: GuardarCuestionarioContenidoRequest
    ): ResponseBody




    @POST("consultarCuestionarioPorTema.php")
    suspend fun consultarCuestionarioPorTema(@Body req: IdTemaRequest): List<CuestionarioRes>



    @POST("modificarCuestionario.php")
    suspend fun modificarCuestionario(
        @Body body: ModificarCuestionarioRequest
    ): String

    @POST("modificarCuestionariosContenido.php")
    suspend fun modificarCuestionariosContenido(
        @Body body: ModificarCuestionariosContenidoRequest
    ): String


    @POST("consultarCuestionariosContenido.php")
    suspend fun consultarCuestionariosContenido(): List<PreguntaRes>

    @POST("eliminarMaterial.php")
    suspend fun eliminarMaterial(@Body body: IdIntRequest): String

    @POST("modificarMaterial.php")
    suspend fun modificarMaterial(@Body body: ModificarMaterialRequest): String

    @POST("eliminarArchivosMaterial.php")
    suspend fun eliminarArchivoMaterial(@Body body: IdIntRequest): String

    @POST("eliminarEnlaceMaterial.php")
    suspend fun eliminarEnlaceMaterial(@Body body: IdIntRequest): String

    // Borrar un cuestionario (cabecera)
    @POST("eliminarCuestionario.php")
    suspend fun eliminarCuestionario(@Body req: IdIntRequest): String

    // Borrar una pregunta del cuestionario
    @POST("eliminarCuestionariosContenido.php")
    suspend fun eliminarCuestionariosContenido(@Body req: IdIntRequest): String



    // ====== ALUMNO – ENTREGAR TAREA ======

    @Multipart
    @POST("guardarHistorialArchivoTarea.php")
    suspend fun guardarHistorialArchivoTarea(
        @Part("json") json: RequestBody,
        @Part archivo: MultipartBody.Part
    ): okhttp3.ResponseBody

    @POST("consultarHistorialTareasPorTarea.php")
    suspend fun consultarHistorialTareasPorTarea(
        @Body body: IdTareasRequest
    ): List<HistorialTareaRes>


    @POST("eliminarHistorialTareas.php")
    suspend fun eliminarHistorialTareas(@Body body: IdIntRequest): okhttp3.ResponseBody


    // ====== COMENTARIOS – EN   TAREA ======



    @POST("guardarComentario.php")
    suspend fun guardarComentario(@Body body: GuardarComentarioRequest): okhttp3.ResponseBody

    @POST("consultarComentarios.php")
    suspend fun consultarComentarios(): List<ComentarioRes>

    @POST("consuoltarComentariosPorUsuarioYTarea.php")
    suspend fun consultarComentariosPorUsuarioYTarea(@Body body: IdUsuarioTareaRequest): List<ComentarioRes>

    @POST("eliminarComentario.php")
    suspend fun eliminarComentario(@Body body: EliminarComentarioRequest): SimpleOkRes



    // data.remote.ApiService (o donde declares tu interfaz)
    @POST("consultarUsuario.php")
    suspend fun consultarUsuario(@Body body: IdIntRequest): List<UsuarioRes>



    @POST("consultarHistorialCuestionarioPorCuestionario.php")
    suspend fun consultarHistorialCuestionarioPorCuestionario(
        @Body body: IdCuestionarioRequest
    ): List<HistorialCuestionarioRes>

    @POST("guardarHistorialCuestionario.php")
    suspend fun guardarHistorialCuestionario(
        @Body body: GuardarHistorialCuestionarioRequest
    ): String





}






