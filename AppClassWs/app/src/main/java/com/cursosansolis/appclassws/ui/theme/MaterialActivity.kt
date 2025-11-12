package com.cursosansolis.appclassws

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.cursosansolis.appclassws.data.remote.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class MaterialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idClaseStr  = intent.getStringExtra("ID_CLASE") ?: "0"
        val nombreClase = intent.getStringExtra("NOMBRE_CLASE") ?: "Sin nombre"
        val idClaseInt  = idClaseStr.toIntOrNull() ?: 0

        setContent {
            MaterialTheme {
                MaterialScreen(
                    idClaseStr = idClaseStr,
                    idClaseInt = idClaseInt,
                    nombreClase = nombreClase,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialScreen(
    idClaseStr: String,
    idClaseInt: Int,
    nombreClase: String,
    onBack: () -> Unit
) {
    val api = remember { RetrofitClient.api }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Temas
    var temas by remember { mutableStateOf<List<Tema>>(emptyList()) }
    var temaSeleccionado by remember { mutableStateOf<Tema?>(null) }
    var temasError by remember { mutableStateOf<String?>(null) }

    // Form
    var titulo by remember { mutableStateOf(TextFieldValue("")) }
    var descripcion by remember { mutableStateOf(TextFieldValue("")) }

    // Estado de guardado
    var idMaterialCreado by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    // Enlaces
    var urlEnlace by remember { mutableStateOf(TextFieldValue("")) }
    var adding by remember { mutableStateOf(false) }

    // Cargar temas
    LaunchedEffect(Unit) {
        runCatching { api.consultarTemasClase(IdClaseRequest(idClaseStr)) }
            .onSuccess { temas = it }
            .onFailure { temasError = it.message ?: "Error al cargar temas" }
    }

    // File picker
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val matId = idMaterialCreado ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                uploadingArchivo(
                    api = api,
                    cr = ctx.contentResolver,
                    idMaterial = matId,
                    uri = uri,
                    onSuccess = { msg = "Archivo subido" },
                    onError = { err -> msg = err }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Material • $nombreClase") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== Selector de Tema =====
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = temaSeleccionado?.titulo
                        ?: if (temas.isEmpty()) "Cargando temas..." else "Seleccione un tema",
                    onValueChange = { /* readOnly */ },
                    readOnly = true,
                    label = { Text("Tema") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()   // si te da error de compilación, cambia por .fillMaxWidth()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    temas.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.titulo) },
                            onClick = {
                                temaSeleccionado = t
                                expanded = false
                            }
                        )
                    }
                }
            }
            temasError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción del material") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val tema = temaSeleccionado ?: run { msg = "Selecciona un tema"; return@Button }
                    if (titulo.text.isBlank()) { msg = "Capture el título"; return@Button }

                    saving = true
                    scope.launch {
                        runCatching {
                            api.guardarMaterial(
                                GuardarMaterialRequest(
                                    id_tema = tema.id,
                                    titulo = titulo.text.trim(),
                                    descripcion = descripcion.text.trim(),
                                    id_clase = idClaseInt
                                )
                            )
                        }.onSuccess { body ->
                            val idNuevo = body.string().trim().toIntOrNull()
                            if (idNuevo != null) {
                                idMaterialCreado = idNuevo
                                msg = "Material creado (id=$idNuevo). Ahora puedes adjuntar."
                            } else {
                                msg = "No se pudo obtener el ID del material"
                            }
                        }.onFailure {
                            msg = it.message ?: "Error al guardar material"
                        }
                        saving = false
                    }
                },
                enabled = !saving && temaSeleccionado != null && idMaterialCreado == null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (saving) "Guardando..." else "Guardar material") }

            // ===== Adjuntos (solo si ya hay id) =====
            val habilitado = idMaterialCreado != null

            OutlinedTextField(
                value = urlEnlace,
                onValueChange = { urlEnlace = it },
                label = { Text("URL a adjuntar") },
                singleLine = true,
                enabled = habilitado,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val matId = idMaterialCreado ?: return@Button
                        if (urlEnlace.text.isBlank()) { msg = "Escribe una URL"; return@Button }
                        adding = true
                        scope.launch {
                            runCatching {
                                api.guardarEnlaceMaterial(
                                    GuardarEnlaceMaterialRequest(matId, urlEnlace.text.trim())
                                )
                            }.onSuccess {
                                msg = "Enlace agregado"
                                urlEnlace = TextFieldValue("")
                            }.onFailure { e ->
                                msg = e.message ?: "Error al agregar enlace"
                            }
                            adding = false
                        }
                    },
                    enabled = habilitado && !adding
                ) { Text(if (adding) "Agregando..." else "Agregar enlace") }

                Button(
                    onClick = { pickFile.launch("*/*") },
                    enabled = habilitado
                ) { Text("Elegir archivo") }
            }

            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

/* ---------- Helpers ---------- */

private suspend fun uploadingArchivo(
    api: ApiService,
    cr: ContentResolver,
    idMaterial: Int,
    uri: Uri,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        // copiar a archivo temporal
        val tmp = File.createTempFile("up_", null)
        cr.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out) }
        }

        val media = cr.getType(uri) ?: "application/octet-stream"
        val filePart = MultipartBody.Part.createFormData(
            name = "archivo",
            filename = tmp.name,
            body = tmp.asRequestBody(media.toMediaType())
        )

        val json = """{"id_material":$idMaterial}"""
            .toRequestBody("application/json".toMediaType())

        api.guardarArchivoMaterial(json, filePart)
        onSuccess()

        tmp.delete()
    } catch (e: Exception) {
        onError(e.message ?: "Error subiendo archivo")
    }
}
