@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import com.cursosansolis.appclassws.ui.theme.ClaseModo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.cursosansolis.appclassws.data.remote.*
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// ---------------- helpers (top-level) ----------------
private fun String?.orElseEmpty() = this ?: ""
private fun String.stripHtml(): String =
    HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

// Normaliza IDs que pueden venir como number o string
private fun JsonElement?.asIdString(): String = when {
    this == null || this.isJsonNull -> ""
    this.isJsonPrimitive && this.asJsonPrimitive.isNumber -> this.asLong.toString()
    else -> this.asString.trim()
}

private const val BASE_URL_PUBLIC = "http://10.0.2.2/ClaseOffLine/api/"
private fun absoluteUrl(relative: String): String {
    if (relative.startsWith("http")) return relative
    val root = BASE_URL_PUBLIC.substringBeforeLast("api/")
    val clean = relative.removePrefix("../")
    return if (clean.startsWith("api/")) root + clean else BASE_URL_PUBLIC + clean
}

// ===== Helpers para abrir enlaces (NUEVO) =====
private fun normalizeUrl(raw: String): String {
    val t = raw.trim()
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
}
private fun Context.openUrlSafe(raw: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(raw)))) }
}

// ----- Confirmación (top-level) -----
private enum class ConfirmType { ANUNCIO, ARCHIVO, ENLACE }
private data class ConfirmState(val type: ConfirmType, val id: String)
// -----------------------------------------------------

@Composable
fun AnunciosScreen(
    modifier: Modifier = Modifier,
    idClase: String,
    nombreClase: String,
    codigoClase: String,
    modo: ClaseModo,
    userId: String
) {
    val ctx = LocalContext.current

    // 🔐 Locks locales independientes
    val prefs = remember { ctx.getSharedPreferences("locks", Context.MODE_PRIVATE) }
    val filesKey = remember(userId, idClase) { "locksF_${userId}_${idClase}" }
    val linksKey = remember(userId, idClase) { "locksL_${userId}_${idClase}" }

    var lockedFiles by remember(filesKey) {
        mutableStateOf(prefs.getStringSet(filesKey, emptySet())?.toSet() ?: emptySet())
    }
    var lockedLinks by remember(linksKey) {
        mutableStateOf(prefs.getStringSet(linksKey, emptySet())?.toSet() ?: emptySet())
    }

    fun lockFile(id: String) {
        lockedFiles = lockedFiles + id
        prefs.edit().putStringSet(filesKey, lockedFiles).apply()
    }
    fun lockLink(id: String) {
        lockedLinks = lockedLinks + id
        prefs.edit().putStringSet(linksKey, lockedLinks).apply()
    }
    fun unlockFile(id: String) {
        lockedFiles = lockedFiles - id
        prefs.edit().putStringSet(filesKey, lockedFiles).apply()
    }
    fun unlockLink(id: String) {
        lockedLinks = lockedLinks - id
        prefs.edit().putStringSet(linksKey, lockedLinks).apply()
    }

    var headerNombre by rememberSaveable { mutableStateOf(nombreClase) }
    var headerCodigo by rememberSaveable { mutableStateOf(codigoClase) }

    var contenido by remember { mutableStateOf("") }
    var loading   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }
    var anuncios  by remember { mutableStateOf(listOf<JsonObject>()) }

    // ---------- file picker ----------
    var anuncioSeleccionadoParaArchivo by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val idAnuncio = anuncioSeleccionadoParaArchivo ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            subirArchivo(ctx, idAnuncio, uri) { ok, msg ->
                if (ok) {
                    lockFile(idAnuncio)
                    loadAnuncios(idClase) { err, data ->
                        error = err
                        anuncios = data ?: emptyList()
                    }
                } else {
                    error = msg
                }
            }
        }
    }

    // ---------- diálogo enlace ----------
    var showDialogEnlace by remember { mutableStateOf(false) }
    var urlEnlace by remember { mutableStateOf("") }
    var idAnuncioParaEnlace by remember { mutableStateOf<String?>(null) }

    // ---------- cargar encabezado + anuncios ----------
    LaunchedEffect(idClase) {
        if (headerCodigo.isBlank() || headerCodigo == "1234") {
            loadClaseHeader(idClase) { nom, cod ->
                if (!nom.isNullOrBlank()) headerNombre = nom
                if (!cod.isNullOrBlank()) headerCodigo = cod
            }
        }
        loadAnuncios(idClase) { err, data ->
            error = err
            anuncios = data ?: emptyList()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        // --------- HEADER ---------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(headerNombre.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.titleLarge)
                if (headerCodigo.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Código: $headerCodigo", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --------- POST ANUNCIO ---------
        Text("Agregar Anuncio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = contenido,
            onValueChange = { contenido = it },
            label = { Text("Escribe tu anuncio") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val sessionUserId = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
                        .getString("id", "") ?: ""
                    if (sessionUserId.isBlank() || contenido.isBlank()) return@Button
                    loading = true
                    RetrofitClient.api.guardarAnuncio(
                        GuardarAnuncioRequest(
                            id_clase = idClase,
                            id_usuario = sessionUserId,
                            mensaje = contenido
                        )
                    ).enqueue(object : Callback<ResponseBody> {
                        override fun onResponse(call: Call<ResponseBody>, resp: Response<ResponseBody>) {
                            loading = false
                            if (resp.isSuccessful) {
                                contenido = ""
                                loadAnuncios(idClase) { err, data ->
                                    error = err
                                    anuncios = data ?: emptyList()
                                }
                            } else error = "Error ${resp.code()}"
                        }
                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                            loading = false; error = "Red: ${t.localizedMessage}"
                        }
                    })
                },
                enabled = !loading
            ) { Text("Publicar anuncio") }
        }

        Spacer(Modifier.height(16.dp))

        // --------- LISTA ---------
        Text("Anuncios", style = MaterialTheme.typography.titleMedium)
        Divider(Modifier.padding(vertical = 4.dp))

        when {
            loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            error != null && anuncios.isEmpty() -> {
                Text("No hay anuncios todavía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (!loading && error == null && anuncios.isEmpty()) {
                item {
                    Text("No hay anuncios todavía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(anuncios, key = { it.get("id")?.asString ?: it.hashCode().toString() }) { an ->

                    val idAnuncio = an.get("id")?.asString.orEmpty()

                    val bloqueadoLocalArchivo = lockedFiles.contains(idAnuncio)
                    val bloqueadoLocalEnlace  = lockedLinks.contains(idAnuncio)

                    AnuncioCard(
                        obj = an,
                        modo = modo,
                        currentUserId = userId,
                        bloqueadoLocalArchivo = bloqueadoLocalArchivo,
                        bloqueadoLocalEnlace  = bloqueadoLocalEnlace,
                        onAdjuntarArchivo = { id ->
                            anuncioSeleccionadoParaArchivo = id
                            filePicker.launch("*/*")
                        },
                        onAgregarEnlace = { id ->
                            idAnuncioParaEnlace = id
                            urlEnlace = ""
                            showDialogEnlace = true
                        },
                        onRefrescar = {
                            loadAnuncios(idClase) { err, data ->
                                error = err
                                anuncios = data ?: emptyList()
                            }
                        }
                    )
                }
            }
        }
    }

    // --------- DIALOG ENLACE ---------
    if (showDialogEnlace) {
        AlertDialog(
            onDismissRequest = { showDialogEnlace = false },
            confirmButton = {
                TextButton(onClick = {
                    val idAnun = idAnuncioParaEnlace ?: return@TextButton
                    if (urlEnlace.isBlank()) return@TextButton
                    RetrofitClient.api.guardarEnlaceAnuncio(
                        GuardarEnlaceRequest(
                            id_anuncios = idAnun,
                            enlace = urlEnlace.trim()   // <= pequeño ajuste
                        )
                    ).enqueue(object : Callback<ResponseBody> {
                        override fun onResponse(call: Call<ResponseBody>, resp: Response<ResponseBody>) {
                            showDialogEnlace = false
                            if (resp.isSuccessful) {
                                //  bloquear localmente este anuncio
                                lockLink(idAnun)
                                loadAnuncios(idClase) { err, data ->
                                    error = err
                                    anuncios = data ?: emptyList()
                                }
                            } else {
                                error = "Error enlace ${resp.code()}"
                            }
                        }
                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                            showDialogEnlace = false
                            error = "Red enlace: ${t.localizedMessage}"
                        }
                    })
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showDialogEnlace = false }) { Text("Cancelar") } },
            title = { Text("Agregar enlace") },
            text  = {
                OutlinedTextField(
                    value = urlEnlace,
                    onValueChange = { urlEnlace = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@Composable
private fun AnuncioCard(
    obj: JsonObject,
    modo: ClaseModo,
    currentUserId: String,
    bloqueadoLocalArchivo: Boolean,
    bloqueadoLocalEnlace: Boolean,
    onAdjuntarArchivo: (idAnuncio: String) -> Unit,
    onAgregarEnlace: (idAnuncio: String) -> Unit,
    onRefrescar: () -> Unit
) {
    val ctx = LocalContext.current
    val idAnuncio = obj.get("id")?.asString.orElseEmpty()
    val currentId = currentUserId.trim()

    // texto
    val mensajeCrudo = obj.get("mensaje")?.asString.orElseEmpty()
    val contenido = mensajeCrudo.stripHtml()

    val autor = obj.get("nombre")?.asString
        ?: obj.get("autor")?.asString ?: ""
    val fecha = obj.get("fecha")?.asString.orElseEmpty()

    // Autor del anuncio (acepta varias keys y normaliza number/string)
    val autorId = obj.get("id_usuario").asIdString()
        .ifBlank { obj.get("idUsuario").asIdString() }
        .ifBlank { obj.get("autor_id").asIdString() }

    // -------- Permisos base --------
    val soyProfe    = (modo == ClaseModo.IMPARTIDAS)
    val esMiAnuncio = autorId.isNotBlank() && autorId == currentId

    // -------- Estado de listas --------
    var archivos by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    LaunchedEffect(idAnuncio) {
        if (idAnuncio.isBlank()) return@LaunchedEffect
        RetrofitClient.api.consultarArchivosPorAnuncio(
            IdAnuncioSimpleRequest(id_anuncio = idAnuncio)
        ).enqueue(object : Callback<JsonArray> {
            override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                archivos = if (resp.isSuccessful)
                    (resp.body() ?: JsonArray()).mapNotNull { it.asJsonObject }
                else emptyList()
            }
            override fun onFailure(call: Call<JsonArray>, t: Throwable) {
                archivos = emptyList()
            }
        })
    }

    // Los enlaces ya vienen (si tu WS los trae en la lista principal)
    val enlaces = obj.getAsJsonArray("enlaces")
        ?.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
        ?: emptyList()

    // -------- Bloqueos --------
    val bloqueadoServer  = obj.get("bloqueado")?.asBoolean ?: false
    val bloqueadoArchivo = bloqueadoServer ||
            bloqueadoLocalArchivo ||
            (esMiAnuncio && archivos.isNotEmpty())       // lock derivado

    val bloqueadoEnlace  = bloqueadoServer ||
            bloqueadoLocalEnlace ||
            (esMiAnuncio && enlaces.isNotEmpty())        // lock derivado

    // 🔒 Reglas finales
    val puedeEliminarAnuncio = soyProfe || esMiAnuncio
    val puedeEliminarAdjunto = soyProfe || esMiAnuncio

    val puedeAdjuntarArchivo = esMiAnuncio && !bloqueadoArchivo
    val puedeAdjuntarEnlace  = esMiAnuncio && !bloqueadoEnlace

    // -------- UI --------
    var confirmar by remember { mutableStateOf<ConfirmState?>(null) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            if (autor.isNotBlank() || fecha.isNotBlank()) {
                Text(
                    text = buildString {
                        if (autor.isNotBlank()) append(autor)
                        if (autor.isNotBlank() && fecha.isNotBlank()) append(" · ")
                        if (fecha.isNotBlank()) append(fecha)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(if (contenido.isNotBlank()) contenido else "—",
                style = MaterialTheme.typography.bodyMedium)

            // ----- ARCHIVOS -----
            if (archivos.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Archivos", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                archivos.forEach { a ->
                    val nombre = a.get("nombre")?.asString ?: "archivo"
                    val rel = a.get("url")?.asString.orElseEmpty()
                    val idArchivo = a.get("id")?.asString.orElseEmpty()
                    if (rel.isNotBlank()) {
                        val full = absoluteUrl(rel)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "• $nombre",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full))) }
                                    }
                            )
                            if (puedeEliminarAdjunto) {
                                IconButton(onClick = {
                                    if (idArchivo.isNotBlank()) {
                                        confirmar = ConfirmState(ConfirmType.ARCHIVO, idArchivo)
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar archivo",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ----- ENLACES -----
            if (enlaces.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Enlaces", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                enlaces.forEachIndexed { idx, e ->
                    val link = e.get("enlace")?.asString.orElseEmpty()
                    val idEnlace = e.get("id")?.asString.orElseEmpty()
                    if (link.isNotBlank()) {
                        val host = runCatching { Uri.parse(normalizeUrl(link)).host }.getOrNull()
                        val etiqueta = host ?: "Enlace ${idx + 1}"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "• $etiqueta",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { ctx.openUrlSafe(link) } // <= abre robusto
                            )
                            if (puedeEliminarAdjunto) {
                                IconButton(onClick = {
                                    if (idEnlace.isNotBlank()) {
                                        confirmar = ConfirmState(ConfirmType.ENLACE, idEnlace)
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar enlace",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ----- Acciones -----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (esMiAnuncio) {
                    OutlinedButton(
                        enabled = idAnuncio.isNotBlank() && puedeAdjuntarArchivo,
                        onClick = { onAdjuntarArchivo(idAnuncio) }
                    ) { Text(if (bloqueadoArchivo) "Bloqueado" else "Subir archivo") }

                    OutlinedButton(
                        enabled = idAnuncio.isNotBlank() && puedeAdjuntarEnlace,
                        onClick = { onAgregarEnlace(idAnuncio) }
                    ) { Text(if (bloqueadoEnlace) "Bloqueado" else "Agregar enlace") }
                }

                Spacer(Modifier.weight(1f))

                if (puedeEliminarAnuncio) {
                    TextButton(
                        enabled = idAnuncio.isNotBlank(),
                        onClick = { confirmar = ConfirmState(ConfirmType.ANUNCIO, idAnuncio) }
                    ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    // ----- Diálogo de confirmación -----
    confirmar?.let { c ->
        val (titulo, mensaje, accion) = when (c.type) {
            ConfirmType.ANUNCIO -> Triple(
                "Eliminar anuncio",
                "¿Deseas eliminar este anuncio?"
            ) {
                if (puedeEliminarAnuncio) {
                    borrarAnuncio(c.id) { ok, err ->
                        confirmar = null
                        if (ok) { Toast.makeText(ctx, "Anuncio eliminado", Toast.LENGTH_SHORT).show(); onRefrescar() }
                        else    { Toast.makeText(ctx, "Error: $err", Toast.LENGTH_SHORT).show() }
                    }
                } else {
                    confirmar = null
                    Toast.makeText(ctx, "No puedes eliminar este anuncio", Toast.LENGTH_SHORT).show()
                }
            }
            ConfirmType.ARCHIVO -> Triple(
                "Eliminar archivo",
                "¿Deseas eliminar este archivo?"
            ) {
                borrarArchivo(c.id) { ok, err ->
                    confirmar = null
                    if (ok) { Toast.makeText(ctx, "Archivo eliminado", Toast.LENGTH_SHORT).show(); onRefrescar() }
                    else    { Toast.makeText(ctx, "Error: $err", Toast.LENGTH_SHORT).show() }
                }
            }
            ConfirmType.ENLACE -> Triple(
                "Eliminar enlace",
                "¿Deseas eliminar este enlace?"
            ) {
                borrarEnlace(c.id) { ok, err ->
                    confirmar = null
                    if (ok) { Toast.makeText(ctx, "Enlace eliminado", Toast.LENGTH_SHORT).show(); onRefrescar() }
                    else    { Toast.makeText(ctx, "Error: $err", Toast.LENGTH_SHORT).show() }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(titulo) },
            text = { Text(mensaje) },
            confirmButton = { TextButton(onClick = accion) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }
        )
    }
}

/** Lista completa (con enlaces si tu WS los trae) */
private fun loadAnuncios(
    idClase: String,
    onResult: (String?, List<JsonObject>?) -> Unit
) {
    RetrofitClient.api.consultarAnunciosArchivosEnlaces(IdClaseRequest(idClase))
        .enqueue(object : Callback<JsonArray> {
            override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                if (resp.isSuccessful) {
                    onResult(null, resp.body()?.mapNotNull { it.asJsonObject } ?: emptyList())
                } else onResult("Error HTTP ${resp.code()}", null)
            }
            override fun onFailure(call: Call<JsonArray>, t: Throwable) {
                onResult("Red: ${t.localizedMessage}", null)
            }
        })
}

private fun loadClaseHeader(
    idClase: String,
    onResult: (nombre: String?, codigo: String?) -> Unit
) {
    val idInt = idClase.toIntOrNull() ?: run { onResult(null, null); return }
    RetrofitClient.api.clasePorId(IdIntRequest(idInt))
        .enqueue(object : Callback<JsonArray> {
            override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                if (!resp.isSuccessful) { onResult(null, null); return }
                val obj = resp.body()?.firstOrNull()?.asJsonObject
                val nombre = obj?.get("nombre")?.asString ?: obj?.get("materia")?.asString
                val codigo = obj?.get("codigo")?.asString
                onResult(nombre, codigo)
            }
            override fun onFailure(call: Call<JsonArray>, t: Throwable) {
                onResult(null, null)
            }
        })
}

private fun subirArchivo(
    context: Context,
    idAnuncio: String,
    uri: Uri,
    onResult: (Boolean, String) -> Unit
) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: run {
            onResult(false, "No se pudo abrir el archivo"); return
        }
        val bytes = input.readBytes(); input.close()

        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val nombre = "archivo_${System.currentTimeMillis()}"

        val fileBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val archivoPart = MultipartBody.Part.createFormData(
            name = "archivo",
            filename = nombre,
            body = fileBody
        )

        val jsonString = """{"id_anuncios": $idAnuncio}"""
        val jsonBody: RequestBody =
            jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

        RetrofitClient.api.subirArchivoAnuncio(archivoPart, jsonBody)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, resp: Response<ResponseBody>) {
                    if (resp.isSuccessful) onResult(true, "Archivo subido")
                    else onResult(false, "HTTP ${resp.code()}")
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    onResult(false, "Red: ${t.localizedMessage}")
                }
            })
    } catch (e: Exception) {
        onResult(false, "Error: ${e.localizedMessage}")
    }
}

// ------------ Borrados ------------
private fun borrarAnuncio(id: String, onDone: (Boolean, String?) -> Unit) {
    RetrofitClient.api.eliminarAnuncio(IdOnlyRequest(id))
        .enqueue(object : Callback<ResponseBody> {
            override fun onResponse(c: Call<ResponseBody>, r: Response<ResponseBody>) {
                onDone(r.isSuccessful, if (r.isSuccessful) null else "HTTP ${r.code()}")
            }
            override fun onFailure(c: Call<ResponseBody>, t: Throwable) {
                onDone(false, t.localizedMessage)
            }
        })
}

private fun borrarArchivo(id: String, onDone: (Boolean, String?) -> Unit) {
    RetrofitClient.api.eliminarArchivoAnuncio(IdOnlyRequest(id))
        .enqueue(object : Callback<ResponseBody> {
            override fun onResponse(c: Call<ResponseBody>, r: Response<ResponseBody>) {
                onDone(r.isSuccessful, if (r.isSuccessful) null else "HTTP ${r.code()}")
            }
            override fun onFailure(c: Call<ResponseBody>, t: Throwable) {
                onDone(false, t.localizedMessage)
            }
        })
}

private fun borrarEnlace(id: String, onDone: (Boolean, String?) -> Unit) {
    RetrofitClient.api.eliminarEnlaceAnuncio(IdOnlyRequest(id))
        .enqueue(object : Callback<ResponseBody> {
            override fun onResponse(c: Call<ResponseBody>, r: Response<ResponseBody>) {
                onDone(r.isSuccessful, if (r.isSuccessful) null else "HTTP ${r.code()}")
            }
            override fun onFailure(c: Call<ResponseBody>, t: Throwable) {
                onDone(false, t.localizedMessage)
            }
        })
}
