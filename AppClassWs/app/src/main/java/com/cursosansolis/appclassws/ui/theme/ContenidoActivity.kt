// ContenidoActivity.kt
package com.cursosansolis.appclassws

import kotlin.jvm.JvmName
import com.cursosansolis.appclassws.data.remote.DownloadUrls

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cursosansolis.appclassws.data.remote.*
import com.cursosansolis.appclassws.ui.theme.ClaseModo
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

// retrofit / gson para la verificación de impartidas
import com.google.gson.JsonArray
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// ===== IMPORTS para preservar nombre/mime =====
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
// ==============================================

// ===== IMPORT ALIAS para evitar choque de clases =====
import com.cursosansolis.appclassws.data.remote.HistorialTareaRes as HistorialTareaResRemote
// =====================================================

// --- Util: quitar etiquetas HTML ---
private fun String.stripHtml(): String =
    HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

class ContenidoActivity : ComponentActivity() {

    private lateinit var vm: ContenidoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idClaseStr  = intent.getStringExtra("ID_CLASE") ?: "0"
        val nombreClase = intent.getStringExtra("NOMBRE_CLASE") ?: "Sin nombre"
        the@ run { /* sanitize */ }
        val idClaseInt  = idClaseStr.toIntOrNull() ?: 0

        val modo: ClaseModo =
            (intent.getSerializableExtra("MODO_CLASE") as? ClaseModo)
                ?: ClaseModo.INSCRITAS

        val userId = getSharedPreferences("session", Context.MODE_PRIVATE)
            .getString("id", "") ?: ""

        vm = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ContenidoViewModel(idClaseStr, idClaseInt) as T
                }
            }
        ).get(ContenidoViewModel::class.java)

        setContent {
            MaterialTheme {
                ContenidoHomeScreen(
                    idClase = idClaseStr,
                    nombreClase = nombreClase,
                    vm = vm,
                    modo = modo,
                    userId = userId,
                    onOpenTema = {
                        startActivity(Intent(this, TemaActivity::class.java).apply {
                            putExtra("ID_CLASE", idClaseStr)
                            putExtra("NOMBRE_CLASE", nombreClase)
                            putExtra("MODO_CLASE", modo)
                        })
                    },
                    onOpenTarea = {
                        startActivity(Intent(this, TareaActivity::class.java).apply {
                            putExtra("ID_CLASE", idClaseStr)
                            putExtra("NOMBRE_CLASE", nombreClase)
                            putExtra("MODO_CLASE", modo)
                        })
                    },
                    onOpenMaterial = {
                        startActivity(Intent(this, MaterialActivity::class.java).apply {
                            putExtra("ID_CLASE", idClaseStr)
                            putExtra("NOMBRE_CLASE", nombreClase)
                            putExtra("MODO_CLASE", modo)
                        })
                    },
                    onOpenCuest = {
                        startActivity(Intent(this, CuestionarioActivity::class.java).apply {
                            putExtra("ID_CLASE", idClaseStr)
                            putExtra("NOMBRE_CLASE", nombreClase)
                            putExtra("MODO_CLASE", modo)
                        })
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.forceReloadAfterReturning()
    }
}

/* -------------------- ViewModel -------------------- */

class ContenidoViewModel(
    private val idClaseStr: String,
    private val idClaseInt: Int
) : ViewModel() {

    private val api = RetrofitClient.api


    val califPorCuestionario = mutableStateMapOf<Int, String>() // ej. "80" o "" si no ha respondido

    var temas by mutableStateOf<List<Tema>>(emptyList()); private set
    var tareas by mutableStateOf<List<Tarea>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    var reloadVersion by mutableStateOf(0); private set
    private fun bumpReload() { reloadVersion++ }

    val enlacesPorTarea = mutableStateMapOf<Int, List<EnlaceTarea>>()
    val archivosPorTarea = mutableStateMapOf<Int, List<ArchivoTarea>>()

    // ===== HISTORIAL ENTREGAS =====
    val historialPorTarea = mutableStateMapOf<Int, List<HistorialTareaResRemote>>()

    val materialesPorTema = mutableStateMapOf<Int, List<Material>>()
    val enlacesMaterialPorMaterial = mutableStateMapOf<Int, List<EnlaceMaterial>>()
    val archivosMaterialPorMaterial = mutableStateMapOf<Int, List<ArchivoMaterial>>()

    val cuestionariosPorTema = mutableStateMapOf<Int, List<Cuestionario>>()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading = true; error = null
            val req = IdClaseRequest(idClaseStr)

            runCatching { api.consultarTemasClase(req) }
                .onSuccess { temas = it }
                .onFailure { error = it.message }

            runCatching { api.consultarTareasClases(req) }
                .onSuccess { tareas = it }
                .onFailure { error = it.message }

            loading = false
        }
    }

    fun forceReloadAfterReturning() = viewModelScope.launch {
        val req = IdClaseRequest(idClaseStr)
        runCatching { api.consultarTemasClase(req) }.onSuccess { temas = it }
        runCatching { api.consultarTareasClases(req) }.onSuccess { tareas = it }

        materialesPorTema.clear()
        enlacesMaterialPorMaterial.clear()
        archivosMaterialPorMaterial.clear()
        cuestionariosPorTema.clear()
        bumpReload()
    }




    fun cargarHistorialCuestionarioDeAlumno(cuestionarioId: Int, userIdInt: Int) = viewModelScope.launch {
        if (userIdInt <= 0) return@launch
        if (califPorCuestionario.containsKey(cuestionarioId)) return@launch

        runCatching {
            // Usa el mismo endpoint/DTO que ya ocupas en la pantalla de Preguntas
            api.consultarHistorialCuestionarioPorCuestionario(
                IdCuestionarioRequest(cuestionarioId)
            )
        }.onSuccess { lista ->
            val mio = lista.firstOrNull { it.id_usuario == userIdInt }
            califPorCuestionario[cuestionarioId] = mio?.calificacion?.toString().orEmpty()
        }.onFailure {
            // cachea vacío para no reintentar en este render
            califPorCuestionario[cuestionarioId] = ""
        }
    }























    // ------- Tema -------
    fun eliminarTema(idTema: Int) = viewModelScope.launch {
        runCatching { api.eliminarTema(IdOnlyRequest(idTema.toString())) }
            .onSuccess { refresh() }
    }

    fun modificarTema(id: Int, nuevoTitulo: String) = viewModelScope.launch {
        runCatching { api.modificarTema(ModificarTemaRequest(id, nuevoTitulo, idClaseInt)) }
            .onSuccess { refresh() }
    }

    // ------- Entregas (historial) -------
    fun eliminarEntrega(histId: Int, tareaId: Int) = viewModelScope.launch {
        runCatching { api.eliminarHistorialTareas(IdIntRequest(histId)) }
            .onSuccess {
                historialPorTarea.remove(tareaId)
                cargarHistorialEntregas(tareaId)
            }.onFailure { error = it.localizedMessage }
    }

    // ------- Adjuntos Tarea -------
    fun cargarAdjuntos(taskId: Int) = viewModelScope.launch {
        if (!enlacesPorTarea.containsKey(taskId)) {
            runCatching { api.consultarEnlaceTareaPorTarea(IdTareasRequest(taskId)) }
                .onSuccess { enlacesPorTarea[taskId] = it }
                .onFailure { enlacesPorTarea[taskId] = emptyList() }
        }
        if (!archivosPorTarea.containsKey(taskId)) {
            runCatching { api.consultarArchivoTareasporTarea(IdTareasRequest(taskId)) }
                .onSuccess { archivosPorTarea[taskId] = it }
                .onFailure { archivosPorTarea[taskId] = emptyList() }
        }
        cargarHistorialEntregas(taskId)
    }

    // ===== Historial entregas =====
    fun cargarHistorialEntregas(taskId: Int) = viewModelScope.launch {
        if (historialPorTarea.containsKey(taskId)) return@launch
        runCatching { api.consultarHistorialTareasPorTarea(IdTareasRequest(taskId)) }
            .onSuccess { historialPorTarea[taskId] = it }
            .onFailure { historialPorTarea[taskId] = emptyList() }
    }

    fun eliminarEnlace(idEnlace: Int, tareaId: Int) = viewModelScope.launch {
        runCatching { api.eliminarEnlaceTarea(IdIntRequest(idEnlace)) }
            .onSuccess {
                enlacesPorTarea.remove(tareaId)
                cargarAdjuntos(tareaId)
            }
    }

    fun guardarEnlace(tareaId: Int, url: String) = viewModelScope.launch {
        runCatching { api.guardarEnlaceTarea(GuardarEnlaceTareaRequest(tareaId, url)) }
            .onSuccess {
                enlacesPorTarea.remove(tareaId)
                cargarAdjuntos(tareaId)
            }
    }

    fun eliminarArchivo(idArchivo: Int, tareaId: Int) = viewModelScope.launch {
        runCatching { api.eliminarArchivoTareas(IdIntRequest(idArchivo)) }
            .onSuccess {
                archivosPorTarea.remove(tareaId)
                cargarAdjuntos(tareaId)
            }
    }

    // ===== Comentarios (públicos) =====
    val comentariosPorTarea = mutableStateMapOf<Int, List<ComentarioRes>>()
    val comentariosLoading = mutableStateMapOf<Int, Boolean>()
    val comentariosError   = mutableStateMapOf<Int, String?>()

    // Cache nombres usuario
    val nombresUsuarios = mutableStateMapOf<Int, String>()

    private suspend fun fetchNombreUsuario(uid: Int): String? =
        runCatching {
            val arr = api.consultarUsuario(IdIntRequest(uid))
            arr.firstOrNull()?.nombre
        }.getOrNull()

    fun cargarNombreUsuario(uid: Int) = viewModelScope.launch {
        if (uid <= 0 || nombresUsuarios.containsKey(uid)) return@launch
        fetchNombreUsuario(uid)?.let { nombresUsuarios[uid] = it }
    }

    private fun precargarNombresDeComentarios(list: List<ComentarioRes>) = viewModelScope.launch {
        list.map { it.id_usuario }
            .filter { it > 0 && !nombresUsuarios.containsKey(it) }
            .distinct()
            .forEach { cargarNombreUsuario(it) }
    }

    fun cargarComentarios(tareaId: Int) = viewModelScope.launch {
        if (comentariosLoading[tareaId] == true) return@launch
        comentariosLoading[tareaId] = true
        comentariosError[tareaId] = null
        runCatching { RetrofitClient.api.consultarComentarios() }
            .onSuccess { all ->
                val list = all.filter { it.id_tarea == tareaId }.sortedBy { it.fecha_comentario }
                comentariosPorTarea[tareaId] = list
                precargarNombresDeComentarios(list)
            }
            .onFailure { e ->
                comentariosError[tareaId] = e.localizedMessage
                comentariosPorTarea[tareaId] = emptyList()
            }
        comentariosLoading[tareaId] = false
    }

    fun agregarComentario(tareaId: Int, userId: String, texto: String) = viewModelScope.launch {
        val uid = userId.toIntOrNull() ?: return@launch
        val clean = texto.trim()
        if (clean.isBlank()) return@launch
        runCatching {
            RetrofitClient.api.guardarComentario(
                GuardarComentarioRequest(tareaId, uid, clean.take(800))
            )
        }.onSuccess {
            comentariosPorTarea.remove(tareaId)
            cargarComentarios(tareaId)
        }.onFailure { e ->
            comentariosError[tareaId] = e.localizedMessage
        }
    }

    fun eliminarMiComentario(comentarioId: Int, tareaId: Int) = viewModelScope.launch {
        runCatching { RetrofitClient.api.eliminarComentario(EliminarComentarioRequest(comentarioId)) }
            .onSuccess {
                val actual = comentariosPorTarea[tareaId].orEmpty()
                comentariosPorTarea[tareaId] = actual.filterNot { it.id == comentarioId }
                cargarComentarios(tareaId)
            }
            .onFailure { e -> comentariosError[tareaId] = e.localizedMessage }
    }

    // ======= util archivos =======
    private data class FileMeta(val displayName: String, val mime: String)

    private fun getFileMeta(cr: ContentResolver, uri: Uri): FileMeta {
        var name = "archivo"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        val mimeFromCr = cr.getType(uri)
        val mime = mimeFromCr ?: run {
            val ext = name.substringAfterLast('.', "")
            if (ext.isNotEmpty()) MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext.lowercase()) else null
        } ?: "application/octet-stream"
        return FileMeta(name, mime)
    }

    private fun copyToTemp(cr: ContentResolver, uri: Uri, displayName: String? = null): File {
        val ext = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
        val tmp = if (ext != null) File.createTempFile("up_", ".$ext") else File.createTempFile("up_", null)
        cr.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out) }
        }
        return tmp
    }

    fun subirArchivoTarea(ctx: Context, tareaId: Int, uri: Uri) = viewModelScope.launch {
        runCatching {
            val cr = ctx.contentResolver
            val meta = getFileMeta(cr, uri)
            val tmp  = copyToTemp(cr, uri, meta.displayName)

            val filePart = MultipartBody.Part.createFormData(
                name = "archivo",
                filename = meta.displayName,
                body = tmp.asRequestBody(meta.mime.toMediaType())
            )
            val json = """{"id_tareas":$tareaId}""".toRequestBody("application/json".toMediaType())
            api.guardarArchivoTarea(json, filePart)
            tmp.delete()
        }.onSuccess {
            archivosPorTarea.remove(tareaId)
            cargarAdjuntos(tareaId)
        }
    }

    // ------- Tarea -------
    fun eliminarTarea(idTarea: Int) = viewModelScope.launch {
        runCatching { api.eliminarTarea(IdIntRequest(idTarea)) }
            .onSuccess { refresh() }
    }

    fun modificarTarea(
        id: Int,
        idTema: Int,
        titulo: String,
        descripcion: String,
        valor: Double,
        fechaEntrega: String
    ) = viewModelScope.launch {
        val body = ModificarTareaRequest(
            id_tema = idTema,
            titulo = titulo,
            descripcion = descripcion,
            valor = valor,
            fecha_entrega = fechaEntrega,
            id = id
        )
        runCatching { api.modificarTarea(body) }
            .onSuccess { refresh() }
    }

    // ------- Material -------
    fun cargarMateriales(temaId: Int) = viewModelScope.launch {
        if (materialesPorTema.containsKey(temaId)) return@launch
        runCatching { api.consultarMaterialIdTema(IdTemaRequest(temaId)).toDomain() }
            .onSuccess { materialesPorTema[temaId] = it }
            .onFailure { materialesPorTema[temaId] = emptyList() }
    }

    fun cargarAdjuntosMaterial(materialId: Int) = viewModelScope.launch {
        if (!enlacesMaterialPorMaterial.containsKey(materialId)) {
            runCatching { api.consultarEnlaceMaterialPorMaterial(IdMaterialRequest(materialId)).toDomain() }
                .onSuccess { enlacesMaterialPorMaterial[materialId] = it }
                .onFailure { enlacesMaterialPorMaterial[materialId] = emptyList() }
        }
        if (!archivosMaterialPorMaterial.containsKey(materialId)) {
            runCatching { api.consultarArchivosMaterialIdMaterial(IdMaterialRequest(materialId)).toDomain() }
                .onSuccess { archivosMaterialPorMaterial[materialId] = it }
                .onFailure { archivosMaterialPorMaterial[materialId] = emptyList() }
        }
    }

    fun guardarEnlaceMaterial(materialId: Int, url: String) = viewModelScope.launch {
        runCatching { api.guardarEnlaceMaterial(GuardarEnlaceMaterialRequest(materialId, url)) }
            .onSuccess {
                enlacesMaterialPorMaterial.remove(materialId)
                cargarAdjuntosMaterial(materialId)
            }
    }

    fun subirArchivoMaterial(ctx: Context, materialId: Int, uri: Uri) = viewModelScope.launch {
        runCatching {
            val cr = ctx.contentResolver
            val meta = getFileMeta(cr, uri)
            val tmp  = copyToTemp(cr, uri, meta.displayName)

            val filePart = MultipartBody.Part.createFormData(
                name = "archivo",
                filename = meta.displayName,
                body = tmp.asRequestBody(meta.mime.toMediaType())
            )
            val json = """{"id_material":$materialId}""".toRequestBody("application/json".toMediaType())
            api.guardarArchivoMaterial(json, filePart)
            tmp.delete()
        }.onSuccess {
            archivosMaterialPorMaterial.remove(materialId)
            cargarAdjuntosMaterial(materialId)
        }
    }

    fun modificarMaterial(idMaterial: Int, idTema: Int, titulo: String, descripcion: String) = viewModelScope.launch {
        val body = ModificarMaterialRequest(id = idMaterial, titulo = titulo, descripcion = descripcion, id_tema = idTema)
        runCatching { api.modificarMaterial(body) }
            .onSuccess {
                materialesPorTema.remove(idTema)
                cargarMateriales(idTema)
            }
            .onFailure { error = it.message }
    }

    fun eliminarMaterial(idMaterial: Int, idTema: Int) = viewModelScope.launch {
        runCatching { api.eliminarMaterial(IdIntRequest(idMaterial)) }
            .onSuccess {
                materialesPorTema.remove(idTema)
                cargarMateriales(idTema)
            }
            .onFailure { error = it.message }
    }

    fun eliminarArchivoMaterial(idArchivo: Int, materialId: Int) = viewModelScope.launch {
        runCatching { api.eliminarArchivoMaterial(IdIntRequest(idArchivo)) }
            .onSuccess {
                archivosMaterialPorMaterial.remove(materialId)
                cargarAdjuntosMaterial(materialId)
            }
    }

    fun eliminarEnlaceMaterial(idEnlace: Int, materialId: Int) = viewModelScope.launch {
        runCatching { api.eliminarEnlaceMaterial(IdIntRequest(idEnlace)) }
            .onSuccess {
                enlacesMaterialPorMaterial.remove(materialId)
                cargarAdjuntosMaterial(materialId)
            }
    }

    // ------- Cuestionarios -------
    fun cargarCuestionarios(temaId: Int) = viewModelScope.launch {
        if (cuestionariosPorTema.containsKey(temaId)) return@launch
        runCatching { api.consultarCuestionarioPorTema(IdTemaRequest(temaId)).toDomainCuestList() }
            .onSuccess { cuestionariosPorTema[temaId] = it }
            .onFailure { cuestionariosPorTema[temaId] = emptyList() }
    }

    fun modificarCuestionario(
        idCuest: Int,
        idTema: Int,
        titulo: String,
        descripcion: String
    ) = viewModelScope.launch {
        val body = ModificarCuestionarioRequest(
            id_tema = idTema,
            titulo = titulo,
            descripcion = descripcion,
            id_clase = idClaseInt,
            id = idCuest
        )
        runCatching { api.modificarCuestionario(body) }
            .onSuccess {
                cuestionariosPorTema.remove(idTema)
                cargarCuestionarios(idTema)
            }
            .onFailure { error = it.message }
    }

    fun eliminarCuestionario(idCuest: Int, deTema: Int) = viewModelScope.launch {
        runCatching { api.eliminarCuestionario(IdIntRequest(idCuest)) }
            .onSuccess {
                cuestionariosPorTema.remove(deTema)
                cargarCuestionarios(deTema)
            }
            .onFailure { error = it.message }
    }

    fun entregarTareaAlumno(
        ctx: Context,
        tareaId: Int,
        userId: String,
        uri: Uri
    ) = viewModelScope.launch {
        runCatching {
            val cr = ctx.contentResolver
            val meta = getFileMeta(cr, uri)
            val tmp  = copyToTemp(cr, uri, meta.displayName)

            val filePart = MultipartBody.Part.createFormData(
                name = "archivo",
                filename = meta.displayName,
                body = tmp.asRequestBody(meta.mime.toMediaType())
            )

            val usuarioId = userId.toIntOrNull() ?: 0
            val json = """{"id_tareas":$tareaId,"id_usuario":$usuarioId}"""
                .toRequestBody("application/json".toMediaType())

            api.guardarHistorialArchivoTarea(json, filePart)

            tmp.delete()
        }.onSuccess {
            enlacesPorTarea.remove(tareaId)
            archivosPorTarea.remove(tareaId)
            historialPorTarea.remove(tareaId)
            cargarAdjuntos(tareaId)
            cargarHistorialEntregas(tareaId)
        }.onFailure { error = it.localizedMessage }
    }
}

/* -------------------- UI -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContenidoHomeScreen(
    idClase: String,
    nombreClase: String,
    vm: ContenidoViewModel,
    modo: ClaseModo,
    userId: String,
    onOpenTema: () -> Unit,
    onOpenTarea: () -> Unit,
    onOpenMaterial: () -> Unit,
    onOpenCuest: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var expandedTaskId by remember { mutableStateOf<Int?>(null) }

    // Resolver si el usuario es profe de ESTA clase
    var esProfe by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(userId, idClase) {
        esProfe = (modo == ClaseModo.IMPARTIDAS)
        if (userId.isNotBlank() && idClase.isNotBlank()) {
            RetrofitClient.api.clasesImpartidas(IdUsuarioRequest(userId))
                .enqueue(object : Callback<JsonArray> {
                    override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                        val arr = resp.body() ?: JsonArray()
                        val soyProfe = arr.any { el ->
                            val o = el.asJsonObject
                            val id = o.get("id_clase")?.asString
                                ?: o.get("idClase")?.asString
                                ?: o.get("id")?.asString
                                ?: ""
                            id == idClase
                        }
                        esProfe = soyProfe
                    }
                    override fun onFailure(call: Call<JsonArray>, t: Throwable) { }
                })
        }
    }

    var tareaParaEntrega by remember { mutableStateOf<Int?>(null) }
    val pickEntregaAlumno = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val idT = tareaParaEntrega
        if (uri != null && idT != null) {
            vm.entregarTareaAlumno(ctx = ctx, tareaId = idT, userId = userId, uri = uri)
        }
        tareaParaEntrega = null
    }

    // ---- Estados UI ----
    var temaEditar by remember { mutableStateOf<Tema?>(null) }
    var nuevoTituloTema by remember { mutableStateOf("") }

    var enlaceAEditar by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var urlNueva by remember { mutableStateOf("") }

    var tareaAEditar by remember { mutableStateOf<Tarea?>(null) }
    var tituloTarea by remember { mutableStateOf("") }
    var valorTarea by remember { mutableStateOf("") }
    var fechaEntregaTarea by remember { mutableStateOf("") }
    var descTarea by remember { mutableStateOf("") }

    var confirmarEliminarEntrega by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirmarEliminarTarea by remember { mutableStateOf<Int?>(null) }

    var cuestionarioAEditar by remember { mutableStateOf<Cuestionario?>(null) }
    var tituloCuest by remember { mutableStateOf("") }
    var descCuest by remember { mutableStateOf("") }
    var confirmarEliminarCuestId by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var materialAEditar by remember { mutableStateOf<Material?>(null) }
    var tituloMat by remember { mutableStateOf("") }
    var descMat by remember { mutableStateOf("") }
    var confirmarEliminarMaterialId by remember { mutableStateOf<Int?>(null) }

    var tareaParaArchivo by remember { mutableStateOf<Int?>(null) }
    val pickArchivoTarea = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val id = tareaParaArchivo
        if (uri != null && id != null) vm.subirArchivoTarea(ctx, id, uri)
        tareaParaArchivo = null
    }

    var materialParaArchivo by remember { mutableStateOf<Int?>(null) }
    val pickArchivoMaterial = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val id = materialParaArchivo
        if (uri != null && id != null) vm.subirArchivoMaterial(ctx, id, uri)
        materialParaArchivo = null
    }

    var enlaceMaterialAEditar by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var urlMaterialNueva by remember { mutableStateOf("") }
    var expandedMaterialId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contenido • $nombreClase") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Atrás") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (esProfe) {
                null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                true -> {
                    item { ContenidoButton("Tema", onOpenTema) }
                    item { ContenidoButton("Tarea", onOpenTarea) }
                    item { ContenidoButton("Material", onOpenMaterial) }
                    item { ContenidoButton("Cuestionario", onOpenCuest) }
                    item { Divider(Modifier.padding(vertical = 8.dp)) }
                }
                false -> item { Divider() }
            }

            when {
                vm.loading && vm.temas.isEmpty() -> {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                }
                vm.error != null -> {
                    item { Text("Error: ${vm.error}", color = MaterialTheme.colorScheme.error) }
                }
                else -> {
                    items(vm.temas, key = { it.id }) { tema ->
                        ElevatedCard {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(tema.titulo.stripHtml(), style = MaterialTheme.typography.titleMedium)
                                    if (esProfe == true) {
                                        Row {
                                            IconButton(onClick = {
                                                temaEditar = tema
                                                nuevoTituloTema = tema.titulo.stripHtml()
                                            }) { Icon(Icons.Filled.Edit, "Editar tema") }
                                            IconButton(onClick = { vm.eliminarTema(tema.id) }) {
                                                Icon(Icons.Filled.Delete, "Eliminar tema")
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                /* --------- TAREAS --------- */
                                val tareasDeTema = remember(vm.tareas) {
                                    vm.tareas.filter { t -> t.id_tema == tema.id }
                                }

                                if (tareasDeTema.isEmpty()) {
                                    Text("Sin tareas para este tema.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    tareasDeTema.forEach { t ->
                                        ElevatedCard(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Column(Modifier.padding(12.dp)) {

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(t.titulo.stripHtml(), style = MaterialTheme.typography.bodyLarge)
                                                    if (esProfe == true) {
                                                        Row {
                                                            IconButton(onClick = {
                                                                tareaAEditar = t
                                                                tituloTarea = t.titulo.stripHtml()
                                                                valorTarea = t.valor.toString()
                                                                fechaEntregaTarea = t.fecha_entrega ?: ""
                                                                descTarea = t.descripcion?.stripHtml() ?: ""
                                                            }) { Icon(Icons.Filled.Edit, "Editar tarea") }
                                                            IconButton(onClick = { confirmarEliminarTarea = t.id }) {
                                                                Icon(Icons.Filled.Delete, "Eliminar tarea")
                                                            }
                                                        }
                                                    }
                                                }

                                                Text("Valor: ${t.valor}", style = MaterialTheme.typography.bodySmall)
                                                Text("Entrega: ${t.fecha_entrega}", style = MaterialTheme.typography.bodySmall)
                                                Spacer(Modifier.height(6.dp))

                                                if (esProfe != true) {
                                                    Spacer(Modifier.height(6.dp))
                                                    TextButton(onClick = {
                                                        tareaParaEntrega = t.id
                                                        pickEntregaAlumno.launch("*/*")
                                                    }) { Text("Entregar archivo") }
                                                }

                                                val isExpanded = expandedTaskId == t.id
                                                Text(
                                                    if (isExpanded) "Ocultar adjuntos" else "Ver adjuntos",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .clickable {
                                                            expandedTaskId = if (isExpanded) null else t.id
                                                            if (!isExpanded) {
                                                                vm.cargarAdjuntos(t.id)
                                                                vm.cargarHistorialEntregas(t.id)
                                                            }
                                                        }
                                                        .padding(vertical = 4.dp)
                                                )

                                                if (isExpanded) {
                                                    val enlaces = vm.enlacesPorTarea[t.id] ?: emptyList()
                                                    val archivos = vm.archivosPorTarea[t.id] ?: emptyList()

                                                    Text("Enlaces:", style = MaterialTheme.typography.labelLarge)
                                                    if (enlaces.isEmpty()) {
                                                        Text("—", style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        enlaces.forEach { e ->
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = "• ${e.enlace.stripHtml()}",
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .padding(start = 8.dp, top = 2.dp)
                                                                        .clickable {
                                                                            runCatching {
                                                                                ctx.startActivity(
                                                                                    Intent(Intent.ACTION_VIEW, Uri.parse(e.url))
                                                                                )
                                                                            }
                                                                        }
                                                                )
                                                                if (esProfe == true) {
                                                                    IconButton(onClick = {
                                                                        enlaceAEditar = e.id to t.id
                                                                        urlNueva = e.enlace
                                                                    }) { Icon(Icons.Filled.Edit, "Editar enlace") }
                                                                    IconButton(onClick = { vm.eliminarEnlace(e.id, t.id) }) {
                                                                        Icon(Icons.Filled.Delete, "Eliminar enlace")
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (esProfe == true) {
                                                        TextButton(onClick = {
                                                            enlaceAEditar = 0 to t.id
                                                            urlNueva = ""
                                                        }) { Text("Agregar enlace") }
                                                    }

                                                    if (esProfe != true) {
                                                        Spacer(Modifier.height(8.dp))
                                                        TextButton(onClick = {
                                                            tareaParaEntrega = t.id
                                                            pickEntregaAlumno.launch("*/*")
                                                        }) { Text("Entregar archivo") }
                                                    }

                                                    Spacer(Modifier.height(8.dp))

                                                    Text("Archivos:", style = MaterialTheme.typography.labelLarge)
                                                    if (archivos.isEmpty()) {
                                                        Text("—", style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        archivos.forEach { a ->
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = "• ${a.nombre.stripHtml()}",
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .padding(start = 8.dp, top = 2.dp)
                                                                        .clickable {
                                                                            val finalUrl = DownloadUrls.tarea(a.id)
                                                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                                                                        }
                                                                )
                                                                if (esProfe == true) {
                                                                    IconButton(onClick = { vm.eliminarArchivo(a.id, t.id) }) {
                                                                        Icon(Icons.Filled.Delete, "Eliminar archivo")
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (esProfe == true) {
                                                        TextButton(onClick = {
                                                            tareaParaArchivo = t.id
                                                            pickArchivoTarea.launch("*/*")
                                                        }) { Text("Agregar archivo") }
                                                    }

                                                    // ====== HISTORIAL DE ENTREGAS ======
                                                    Spacer(Modifier.height(10.dp))
                                                    Text("Entregas:", style = MaterialTheme.typography.labelLarge)

                                                    val todasEntregas = vm.historialPorTarea[t.id] ?: emptyList()
                                                    val uid = userId.toIntOrNull() ?: -1
                                                    val entregasMostradas =
                                                        if (esProfe == true) todasEntregas
                                                        else todasEntregas.filter { it.id_usuario == uid }

                                                    if (entregasMostradas.isEmpty()) {
                                                        Text("—", style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        entregasMostradas.forEach { h ->
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = "• ${h.nombre} (${h.fecha})",
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .padding(start = 8.dp, top = 2.dp)
                                                                        .clickable {
                                                                            runCatching {
                                                                                val finalUrl = if ((h.url ?: "").startsWith("http")) h.url!!
                                                                                else DownloadUrls.historial(h.id)
                                                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                                                                            }
                                                                        }
                                                                )
                                                                if (esProfe != true && h.id_usuario == uid) {
                                                                    IconButton(onClick = {
                                                                        confirmarEliminarEntrega = h.id to t.id
                                                                    }) { Icon(Icons.Filled.Delete, "Anular entrega") }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // ====== COMENTARIOS ======
                                                    Spacer(Modifier.height(10.dp))
                                                    Text("Comentarios:", style = MaterialTheme.typography.labelLarge)

                                                    LaunchedEffect(t.id) { vm.cargarComentarios(t.id) }

                                                    val listaCom = vm.comentariosPorTarea[t.id] ?: emptyList()
                                                    val cLoading = vm.comentariosLoading[t.id] == true
                                                    val cError   = vm.comentariosError[t.id]
                                                    val uidCurrent = userId.toIntOrNull() ?: -1

                                                    if (cLoading) {
                                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                                    } else if (cError != null) {
                                                        Text("Error: $cError", color = MaterialTheme.colorScheme.error)
                                                    } else if (listaCom.isEmpty()) {
                                                        Text("—", style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        listaCom.forEach { cmt ->
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 2.dp)
                                                            ) {
                                                                val esMio = (cmt.id_usuario == uidCurrent)
                                                                val nombreMostrado = when {
                                                                    esMio -> "Tú"
                                                                    else -> vm.nombresUsuarios[cmt.id_usuario] ?: "Usuario ${cmt.id_usuario}"
                                                                }
                                                                LaunchedEffect(cmt.id_usuario) {
                                                                    if (!esMio && vm.nombresUsuarios[cmt.id_usuario] == null) {
                                                                        vm.cargarNombreUsuario(cmt.id_usuario)
                                                                    }
                                                                }

                                                                Column(Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = "$nombreMostrado: ${cmt.comentario}",
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                    )
                                                                    Text(cmt.fecha_comentario, style = MaterialTheme.typography.bodySmall)
                                                                }
                                                                if (esMio) {
                                                                    IconButton(onClick = {
                                                                        vm.eliminarMiComentario(cmt.id, t.id)
                                                                    }) {
                                                                        Icon(Icons.Filled.Delete, "Eliminar comentario")
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Spacer(Modifier.height(6.dp))
                                                    var nuevoComentario by remember(t.id) { mutableStateOf("") }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        OutlinedTextField(
                                                            value = nuevoComentario,
                                                            onValueChange = { nuevoComentario = it },
                                                            modifier = Modifier.weight(1f),
                                                            placeholder = { Text("Escribe un comentario…") },
                                                            singleLine = true
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Button(onClick = {
                                                            vm.agregarComentario(t.id, userId, nuevoComentario)
                                                            nuevoComentario = ""
                                                        }) { Text("Enviar") }
                                                    }
                                                    // ====== FIN COMENTARIOS ======
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                /* --------- MATERIALES --------- */
                                LaunchedEffect(tema.id, vm.reloadVersion) {
                                    vm.cargarMateriales(tema.id)
                                    vm.cargarCuestionarios(tema.id)
                                }

                                val materiales = vm.materialesPorTema[tema.id] ?: emptyList()
                                if (materiales.isNotEmpty()) {
                                    Text("Materiales:", style = MaterialTheme.typography.titleSmall)

                                    materiales.forEach { m ->
                                        ElevatedCard(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Column(Modifier.padding(12.dp)) {

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(m.titulo.stripHtml(), style = MaterialTheme.typography.bodyLarge)
                                                        m.descripcion?.takeIf { it.isNotBlank() }?.let {
                                                            Text(it.stripHtml(), style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    }
                                                    if (esProfe == true) {
                                                        Row {
                                                            IconButton(onClick = {
                                                                materialAEditar = m
                                                                tituloMat = m.titulo.stripHtml()
                                                                descMat = m.descripcion?.stripHtml() ?: ""
                                                            }) { Icon(Icons.Filled.Edit, "Editar material") }
                                                            IconButton(onClick = { confirmarEliminarMaterialId = m.id }) {
                                                                Icon(Icons.Filled.Delete, "Eliminar material")
                                                            }
                                                        }
                                                    }
                                                }

                                                val matExpanded = expandedMaterialId == m.id
                                                Text(
                                                    if (matExpanded) "Ocultar adjuntos" else "Ver adjuntos",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .clickable {
                                                            expandedMaterialId = if (matExpanded) null else m.id
                                                            if (!matExpanded) vm.cargarAdjuntosMaterial(m.id)
                                                        }
                                                        .padding(vertical = 4.dp)
                                                )

                                                if (matExpanded) {
                                                    val enlacesM = vm.enlacesMaterialPorMaterial[m.id] ?: emptyList()
                                                    val archivosM = vm.archivosMaterialPorMaterial[m.id] ?: emptyList()

                                                    Text("Enlaces:", style = MaterialTheme.typography.labelLarge)
                                                    if (enlacesM.isEmpty()) {
                                                        Text("—", style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        enlacesM.forEach { e ->
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = "• ${e.enlace.stripHtml()}",
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .padding(start = 8.dp, top = 2.dp)
                                                                        .clickable {
                                                                            runCatching {
                                                                                ctx.startActivity(
                                                                                    Intent(Intent.ACTION_VIEW, Uri.parse(e.url))
                                                                                )
                                                                            }
                                                                        }
                                                                )
                                                                if (esProfe == true) {
                                                                    IconButton(onClick = {
                                                                        enlaceMaterialAEditar = e.id to m.id
                                                                        urlMaterialNueva = e.enlace
                                                                    }) { Icon(Icons.Filled.Edit, "Editar enlace") }
                                                                    IconButton(onClick = {
                                                                        vm.eliminarEnlaceMaterial(e.id, m.id)
                                                                    }) { Icon(Icons.Filled.Delete, "Eliminar enlace") }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (esProfe == true) {
                                                        TextButton(onClick = {
                                                            enlaceMaterialAEditar = 0 to m.id
                                                            urlMaterialNueva = ""
                                                        }) { Text("Agregar enlace") }
                                                    }

                                                    Spacer(Modifier.height(8.dp))

                                                    Text("Archivos:", style = MaterialTheme.typography.labelLarge)
                                                    if (archivosM.isEmpty()) {
                                                        Text("—", style = MaterialTheme.typography.bodySmall)
                                                    } else {
                                                        archivosM.forEach { a ->
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = "• ${a.nombre.stripHtml()}",
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .padding(start = 8.dp, top = 2.dp)
                                                                        .clickable {
                                                                            val finalUrl = DownloadUrls.material(a.id)
                                                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                                                                        }
                                                                )
                                                                if (esProfe == true) {
                                                                    IconButton(onClick = {
                                                                        vm.eliminarArchivoMaterial(a.id, m.id)
                                                                    }) { Icon(Icons.Filled.Delete, "Eliminar archivo") }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (esProfe == true) {
                                                        TextButton(onClick = {
                                                            materialParaArchivo = m.id
                                                            pickArchivoMaterial.launch("*/*")
                                                        }) { Text("Agregar archivo") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                /* --------- CUESTIONARIOS --------- */
                                val cuestionarios = vm.cuestionariosPorTema[tema.id] ?: emptyList()
                                if (cuestionarios.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Cuestionarios:", style = MaterialTheme.typography.titleSmall)

                                    cuestionarios.forEach { c ->
                                        ElevatedCard(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Column(Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(c.titulo.stripHtml(), style = MaterialTheme.typography.bodyLarge)
                                                        c.descripcion?.takeIf { it.isNotBlank() }?.let {
                                                            Text(it.stripHtml(), style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    }

                                                    // ===== NUEVO: Chip con calificación del alumno (solo en modo alumno) =====
                                                    if (esProfe != true) {
                                                        val uid = userId.toIntOrNull() ?: -1
                                                        LaunchedEffect(c.id, uid) {
                                                            vm.cargarHistorialCuestionarioDeAlumno(c.id, uid)
                                                        }
                                                        val cal = vm.califPorCuestionario[c.id].orEmpty()

                                                        AssistChip(
                                                            onClick = { /* no-op */ },
                                                            enabled = false,
                                                            label = {
                                                                Text(if (cal.isBlank()) "Sin responder" else "Calificación: ${cal}%")
                                                            }
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    // ===== FIN NUEVO =====

                                                    Row {
                                                        if (esProfe == true) {
                                                            IconButton(onClick = {
                                                                cuestionarioAEditar = c
                                                                tituloCuest = c.titulo.stripHtml()
                                                                descCuest = c.descripcion?.stripHtml() ?: ""
                                                            }) { Icon(Icons.Filled.Edit, "Editar cuestionario") }
                                                            IconButton(onClick = { confirmarEliminarCuestId = c.id to tema.id }) {
                                                                Icon(Icons.Filled.Delete, "Eliminar cuestionario")
                                                            }
                                                        }
                                                        TextButton(onClick = {
                                                            ctx.startActivity(
                                                                Intent(ctx, CuestionarioEditorActivity::class.java).apply {
                                                                    putExtra("ID_CUESTIONARIO", c.id)
                                                                    putExtra("ID_TEMA", tema.id)
                                                                    putExtra("ID_CLASE", idClase)
                                                                    putExtra("NOMBRE_CLASE", nombreClase)
                                                                    putExtra("SOLO_LECTURA", esProfe != true)
                                                                }
                                                            )
                                                        }) { Text("Preguntas") }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    // ===== Diálogos =====

    // Editar tema
    temaEditar?.let {
        AlertDialog(
            onDismissRequest = { temaEditar = null },
            title = { Text("Editar tema") },
            text = {
                OutlinedTextField(
                    value = nuevoTituloTema,
                    onValueChange = { nuevoTituloTema = it },
                    label = { Text("Título") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nuevoTituloTema.isNotBlank()) {
                        vm.modificarTema(it.id, nuevoTituloTema.trim())
                        temaEditar = null
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { temaEditar = null }) { Text("Cancelar") } }
        )
    }

    enlaceAEditar?.let { (idEnlace, idTarea) ->
        AlertDialog(
            onDismissRequest = { enlaceAEditar = null },
            title = { Text(if (idEnlace == 0) "Agregar enlace" else "Editar enlace") },
            text = {
                OutlinedTextField(value = urlNueva, onValueChange = { urlNueva = it }, label = { Text("URL") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (urlNueva.isNotBlank()) {
                        if (idEnlace != 0) vm.eliminarEnlace(idEnlace, idTarea)
                        vm.guardarEnlace(idTarea, urlNueva.trim())
                        enlaceAEditar = null
                    }
                }) { Text(if (idEnlace == 0) "Agregar" else "Guardar") }
            },
            dismissButton = { TextButton(onClick = { enlaceAEditar = null }) { Text("Cancelar") } }
        )
    }

    enlaceMaterialAEditar?.let { (idEnlace, idMaterial) ->
        AlertDialog(
            onDismissRequest = { enlaceMaterialAEditar = null },
            title = { Text(if (idEnlace == 0) "Agregar enlace (material)" else "Editar enlace (material)") },
            text = {
                OutlinedTextField(value = urlMaterialNueva, onValueChange = { urlMaterialNueva = it }, label = { Text("URL") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (urlMaterialNueva.isNotBlank()) {
                        vm.guardarEnlaceMaterial(idMaterial, urlMaterialNueva.trim())
                        enlaceMaterialAEditar = null
                    }
                }) { Text(if (idEnlace == 0) "Agregar" else "Guardar") }
            },
            dismissButton = { TextButton(onClick = { enlaceMaterialAEditar = null }) { Text("Cancelar") } }
        )
    }

    tareaAEditar?.let { t ->
        AlertDialog(
            onDismissRequest = { tareaAEditar = null },
            title = { Text("Editar tarea") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = tituloTarea, onValueChange = { tituloTarea = it }, label = { Text("Título") })
                    OutlinedTextField(value = valorTarea,  onValueChange = { valorTarea  = it }, label = { Text("Valor (número)") })
                    OutlinedTextField(value = fechaEntregaTarea, onValueChange = { fechaEntregaTarea = it }, label = { Text("Fecha (YYYY-MM-DD HH:mm:ss)") })
                    OutlinedTextField(value = descTarea, onValueChange = { descTarea = it }, label = { Text("Descripción") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = valorTarea.replace(',', '.').toDoubleOrNull() ?: 0.0
                    vm.modificarTarea(
                        id = t.id,
                        idTema = t.id_tema,
                        titulo = tituloTarea.trim(),
                        descripcion = descTarea.trim(),
                        valor = v,
                        fechaEntrega = fechaEntregaTarea.trim()
                    )
                    tareaAEditar = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { tareaAEditar = null }) { Text("Cancelar") } }
        )
    }

    materialAEditar?.let { m ->
        AlertDialog(
            onDismissRequest = { materialAEditar = null },
            title = { Text("Editar material") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = tituloMat, onValueChange = { tituloMat = it }, label = { Text("Título") }, singleLine = true)
                    OutlinedTextField(value = descMat, onValueChange = { descMat = it }, label = { Text("Descripción") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.modificarMaterial(m.id, m.id_tema, tituloMat.trim(), descMat.trim())
                    materialAEditar = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { materialAEditar = null }) { Text("Cancelar") } }
        )
    }

    confirmarEliminarMaterialId?.let { idMat ->
        AlertDialog(
            onDismissRequest = { confirmarEliminarMaterialId = null },
            title = { Text("Eliminar material") },
            text = { Text("¿Seguro que deseas eliminar este material?") },
            confirmButton = {
                TextButton(onClick = {
                    val temaId = vm.materialesPorTema.entries
                        .firstOrNull { (_, list) -> list.any { it.id == idMat } }
                        ?.key ?: 0
                    vm.eliminarMaterial(idMat, temaId)
                    confirmarEliminarMaterialId = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminarMaterialId = null }) { Text("Cancelar") } }
        )
    }

    confirmarEliminarCuestId?.let { (idCuest, idTema) ->
        AlertDialog(
            onDismissRequest = { confirmarEliminarCuestId = null },
            title = { Text("Eliminar cuestionario") },
            text = { Text("¿Seguro que deseas eliminar este cuestionario?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminarCuestionario(idCuest, idTema)
                    confirmarEliminarCuestId = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminarCuestId = null }) { Text("Cancelar") } }
        )
    }

    confirmarEliminarEntrega?.let { (histId, tareaId) ->
        AlertDialog(
            onDismissRequest = { confirmarEliminarEntrega = null },
            title = { Text("Anular entrega") },
            text  = { Text("¿Seguro que deseas eliminar tu entrega?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminarEntrega(histId, tareaId)
                    confirmarEliminarEntrega = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminarEntrega = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ContenidoButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Filled.List, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

/* ===== MAPEOS *Res → dominio ===== */

private fun MaterialRes.toDomain() = Material(id, id_tema, titulo, descripcion, 0)
private fun EnlaceMaterialRes.toDomain() = EnlaceMaterial(id, id_material, enlace, fecha, url)
private fun ArchivoMaterialRes.toDomain() = ArchivoMaterial(id, id_material, nombre, fecha, url)
@JvmName("toDomainMaterialResList") private fun List<MaterialRes>.toDomain() = map { it.toDomain() }
@JvmName("toDomainEnlaceMaterialResList") private fun List<EnlaceMaterialRes>.toDomain() = map { it.toDomain() }
@JvmName("toDomainArchivoMaterialResList") private fun List<ArchivoMaterialRes>.toDomain() = map { it.toDomain() }

private fun CuestionarioRes.toDomainCuest() =
    Cuestionario(id, id_tema, titulo, descripcion, id_clase)

@JvmName("toDomainCuestionarioResList")
private fun List<CuestionarioRes>.toDomainCuestList() = map { it.toDomainCuest() }
