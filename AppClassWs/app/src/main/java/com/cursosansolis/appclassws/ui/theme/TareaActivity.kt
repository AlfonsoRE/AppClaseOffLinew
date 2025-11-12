package com.cursosansolis.appclassws

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cursosansolis.appclassws.data.remote.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TareaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idClaseStr  = intent.getStringExtra("ID_CLASE") ?: "0"
        val nombreClase = intent.getStringExtra("NOMBRE_CLASE") ?: "Sin nombre"
        val idClaseInt  = idClaseStr.toIntOrNull() ?: 0

        val vm = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TareaViewModel(idClaseStr, idClaseInt) as T
                }
            }
        ).get(TareaViewModel::class.java)

        setContent {
            MaterialTheme {
                TareaScreen(
                    nombreClase = nombreClase,
                    vm = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}

/* -------------------- ViewModel -------------------- */

class TareaViewModel(
    private val idClaseStr: String, // para consultar temas
    private val idClaseInt: Int     // para guardar tarea
) : ViewModel() {

    private val api = RetrofitClient.api

    var temas by mutableStateOf<List<Tema>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    var lastTaskId by mutableStateOf<Int?>(null); private set
    var enlaceStatus by mutableStateOf<String?>(null); private set
    var archivoStatus by mutableStateOf<String?>(null); private set

    init { loadTemas() }

    fun loadTemas() = viewModelScope.launch {
        loading = true; error = null
        runCatching { api.consultarTemasClase(IdClaseRequest(idClaseStr)) }
            .onSuccess { temas = it }
            .onFailure { error = it.message ?: "Error al cargar temas" }
        loading = false
    }

    fun guardarTarea(
        idTema: Int,
        titulo: String,
        descripcion: String,
        valor: String,
        fechaEntrega: String
    ) = viewModelScope.launch {
        loading = true; error = null; lastTaskId = null; enlaceStatus = null; archivoStatus = null
        val valorNum = valor.toDoubleOrNull() ?: 0.0
        runCatching<ResponseBody> {
            api.guardarTarea(
                GuardarTareaRequest(
                    id_tema = idTema,
                    titulo = titulo,
                    descripcion = descripcion,
                    valor = valorNum,
                    fecha_entrega = fechaEntrega,
                    id_clase = idClaseInt
                )
            )
        }.onSuccess { body ->
            lastTaskId = body.string().trim().toIntOrNull()
        }.onFailure { error = it.message ?: "No se pudo guardar la tarea" }
        loading = false
    }

    fun guardarEnlace(idTarea: Int, url: String) = viewModelScope.launch {
        enlaceStatus = null
        runCatching<ResponseBody> {
            api.guardarEnlaceTarea(GuardarEnlaceTareaRequest(id_tareas = idTarea, enlace = url))
        }.onSuccess { enlaceStatus = "Enlace agregado correctamente." }
            .onFailure { enlaceStatus = "Error al agregar enlace: ${it.message}" }
    }

    fun subirArchivo(jsonBody: RequestBody, filePart: MultipartBody.Part) = viewModelScope.launch {
        archivoStatus = null
        runCatching<ResponseBody> { api.guardarArchivoTarea(json = jsonBody, archivo = filePart) }
            .onSuccess { archivoStatus = "Archivo subido correctamente." }
            .onFailure { archivoStatus = "Error al subir archivo: ${it.message}" }
    }
}

/* -------------------- UI -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TareaScreen(
    nombreClase: String,
    vm: TareaViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

    // Estados de formulario
    var expanded by remember { mutableStateOf(false) }
    var selectedTemaId by remember { mutableStateOf<Int?>(null) }
    var selectedTemaTitle by remember { mutableStateOf("Seleccione un tema") }

    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var fechaEntrega by remember { mutableStateOf("") } // se rellena con el picker

    // Picker de fecha + hora
    fun openDateTimePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            ctx,
            { _, y, m, d ->
                TimePickerDialog(
                    ctx,
                    { _, hour, minute ->
                        cal.set(y, m, d, hour, minute, 0)
                        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        fechaEntrega = fmt.format(cal.time)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Lanzador para seleccionar archivo
    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        pickedFileUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tarea • $nombreClase") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Tema ---
            item {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedTemaTitle,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tema") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        vm.temas.forEach { tema ->
                            DropdownMenuItem(
                                text = { Text(tema.titulo) },
                                onClick = {
                                    selectedTemaId = tema.id
                                    selectedTemaTitle = tema.titulo
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it },
                    label = { Text("Valor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- Fecha de entrega con picker ---
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = fechaEntrega,
                        onValueChange = { /* readOnly */ },
                        readOnly = true,
                        label = { Text("Fecha de entrega (YYYY-MM-DD HH:mm:ss)") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { openDateTimePicker() }) { Text("Elegir") }
                }
            }

            item {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción de la tarea") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }

            // --- Guardar tarea ---
            item {
                Button(
                    enabled = !vm.loading &&
                            selectedTemaId != null &&
                            titulo.isNotBlank() &&
                            fechaEntrega.isNotBlank(),
                    onClick = {
                        vm.guardarTarea(
                            idTema = selectedTemaId!!,
                            titulo = titulo.trim(),
                            descripcion = descripcion.trim(),
                            valor = valor.trim(),
                            fechaEntrega = fechaEntrega.trim()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (vm.loading) "Guardando..." else "Guardar tarea")
                }
            }

            // --- Mensajes de estado ---
            item {
                vm.lastTaskId?.let { id ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Tarea creada", style = MaterialTheme.typography.titleMedium)
                            Text("ID: $id. Ahora puedes adjuntar enlaces y archivos.")
                        }
                    }
                }
                vm.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            }

            // --- Acciones: agregar enlace / subir archivo ---
            item {
                val taskId = vm.lastTaskId
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Agregar enlace
                    var enlace by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = enlace,
                        onValueChange = { enlace = it },
                        label = { Text("URL a adjuntar") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = taskId != null
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = taskId != null && enlace.isNotBlank(),
                            onClick = { vm.guardarEnlace(taskId!!, enlace.trim()) }
                        ) { Text("Agregar enlace") }

                        // Subir archivo
                        Button(
                            enabled = taskId != null,
                            onClick = { pickFile.launch("*/*") }
                        ) { Text("Elegir archivo") }
                    }

                    // Si eligieron archivo, construir multipart y subir
                    val cacheDir = ctx.cacheDir
                    pickedFileUri?.let { uri ->
                        LaunchedEffect(uri) {
                            val (jsonBody, filePart) = buildMultipartFromUri(
                                resolver = ctx.contentResolver,
                                cacheDir = cacheDir,
                                uri = uri,
                                taskId = taskId ?: return@LaunchedEffect
                            )
                            vm.subirArchivo(jsonBody, filePart)
                            pickedFileUri = null
                        }
                    }

                    // Status de acciones
                    vm.enlaceStatus?.let { Text(it) }
                    vm.archivoStatus?.let { Text(it) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/* -------------------- Helpers: multipart desde URI -------------------- */

private fun buildMultipartFromUri(
    resolver: ContentResolver,
    cacheDir: File,
    uri: Uri,
    taskId: Int
): Pair<RequestBody, MultipartBody.Part> {

    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val fileName = resolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex("_display_name")
        if (idx != -1 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment ?: "archivo"
    } ?: "archivo"

    // Copiar a un archivo temporal para OkHttp
    val temp = File.createTempFile("up_", "_tmp", cacheDir)
    resolver.openInputStream(uri)?.use { input ->
        temp.outputStream().use { output -> input.copyTo(output) }
    }

    val requestFile = temp.asRequestBody(mime.toMediaTypeOrNull())
    val filePart = MultipartBody.Part.createFormData("archivo", fileName, requestFile)

    val json = """{"id_tareas": $taskId}"""
    val jsonBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

    return jsonBody to filePart
}
