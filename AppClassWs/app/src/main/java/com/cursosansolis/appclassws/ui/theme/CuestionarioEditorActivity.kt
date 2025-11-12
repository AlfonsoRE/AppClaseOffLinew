// CuestionarioEditorActivity.kt
package com.cursosansolis.appclassws

import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cursosansolis.appclassws.data.remote.*
import kotlinx.coroutines.launch
import kotlin.math.round

class CuestionarioEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idCuestionario = intent.getIntExtra("ID_CUESTIONARIO", 0)
        val nombreClase    = intent.getStringExtra("NOMBRE_CLASE") ?: "Clase"
        val soloLectura    = intent.getBooleanExtra("SOLO_LECTURA", false)

        // userId desde la sesión (solo lo usamos en modo alumno)
        val userId = getSharedPreferences("session", MODE_PRIVATE)
            .getString("id", "")?.toIntOrNull() ?: 0

        val vm = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return EditorVM(idCuestionario, userId) as T
                }
            }
        )[EditorVM::class.java]

        setContent {
            MaterialTheme {
                EditorScreen(
                    nombreClase = nombreClase,
                    vm = vm,
                    soloLectura = soloLectura
                ) { finish() }
            }
        }
    }
}

/* -------------------- ViewModel -------------------- */

class EditorVM(
    private val idCuestionario: Int,
    private val userId: Int
) : ViewModel() {
    private val api = RetrofitClient.api

    var preguntas by mutableStateOf<List<Pregunta>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    // ====== estado para alumno ======
    var yaRespondido by mutableStateOf(false); private set
    var enviando by mutableStateOf(false); private set
    var calificacionGuardada by mutableStateOf<String?>(null); private set
    // idPregunta -> respuesta elegida
    var respuestas by mutableStateOf<Map<Int, String>>(emptyMap()); private set
    // mostrar soluciones de las falladas tras enviar
    var mostrarSoluciones by mutableStateOf(false); private set
    // =================================

    init { reload() }

    fun reload() = viewModelScope.launch {
        loading = true; error = null
        runCatching { api.consultarCuestionariosContenido() }
            .onSuccess { all -> preguntas = all.toDomain().filter { it.id_cuestionario == idCuestionario } }
            .onFailure { error = it.message }
        loading = false

        // si hay usuario, comprobar si ya respondió
        if (userId > 0) {
            checkHistorial()
        }
    }

    private suspend fun checkHistorial() {
        runCatching {
            api.consultarHistorialCuestionarioPorCuestionario(IdCuestionarioRequest(idCuestionario))
        }.onSuccess { lista ->
            val mio = lista.firstOrNull { it.id_usuario == userId }
            yaRespondido = mio != null
            calificacionGuardada = mio?.calificacion
        }
    }

    fun marcarRespuesta(idPregunta: Int, opcion: String) {
        respuestas = respuestas.toMutableMap().apply { put(idPregunta, opcion) }
    }

    fun enviarRespuestas(onOk: () -> Unit) = viewModelScope.launch {
        if (userId <= 0 || preguntas.isEmpty() || yaRespondido) return@launch

        enviando = true; error = null

        // correctas vs elegidas
        val total = preguntas.size
        val correctas = preguntas.count { p ->
            val elegida = respuestas[p.id]?.trim().orEmpty()
            elegida.equals(p.respuesta.trim(), ignoreCase = true)
        }
        val porcentaje = if (total == 0) 0.0 else (correctas.toDouble() / total.toDouble()) * 100.0
        val pct2 = round(porcentaje * 100) / 100.0
        val calif = "%,.2f".format(pct2)

        runCatching {
            api.guardarHistorialCuestionario(
                GuardarHistorialCuestionarioRequest(
                    id_cuestionario = idCuestionario,
                    id_usuario = userId,
                    calificacion = calif
                )
            )
        }.onSuccess {
            calificacionGuardada = calif
            yaRespondido = true
            mostrarSoluciones = true   // solo para mostrar las correctas en las falladas
            onOk()
        }.onFailure { e ->
            error = e.localizedMessage
        }

        enviando = false
    }

    // ===== Profesor =====
    fun eliminarPregunta(idPregunta: Int, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        loading = true; error = null
        runCatching { api.eliminarCuestionariosContenido(IdIntRequest(idPregunta)) }
            .onSuccess { reload(); onDone?.invoke() }
            .onFailure { error = it.message }
        loading = false
    }

    fun agregarPregunta(
        enunciado: String,
        op1: String?, op2: String?, op3: String?, op4: String?,
        resp: String,
        onDone: () -> Unit
    ) = viewModelScope.launch {
        loading = true; error = null
        val body = GuardarCuestionarioContenidoRequest(
            id_cuestionario = idCuestionario,
            pregunta = enunciado,
            opcion1 = op1, opcion2 = op2, opcion3 = op3, opcion4 = op4,
            respuesta = resp
        )
        runCatching { api.guardarCuestionariosContenido(body) }
            .onSuccess { reload(); onDone() }
            .onFailure { error = it.message }
        loading = false
    }

    fun modificarPregunta(
        id: Int,
        enunciado: String,
        op1: String?, op2: String?, op3: String?, op4: String?,
        resp: String,
        onDone: () -> Unit
    ) = viewModelScope.launch {
        loading = true; error = null
        val body = ModificarCuestionariosContenidoRequest(
            id = id,
            pregunta = enunciado,
            opcion1 = op1, opcion2 = op2, opcion3 = op3, opcion4 = op4,
            respuesta = resp
        )
        runCatching { api.modificarCuestionariosContenido(body) }
            .onSuccess { reload(); onDone() }
            .onFailure { error = it.message }
        loading = false
    }
}

/* -------------------- UI -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    nombreClase: String,
    vm: EditorVM,
    soloLectura: Boolean,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

    // ====== estados de diálogo para profesor ======
    var editando by remember { mutableStateOf<Pregunta?>(null) }
    var enunciado by remember { mutableStateOf("") }
    var op1 by remember { mutableStateOf("") }
    var op2 by remember { mutableStateOf("") }
    var op3 by remember { mutableStateOf("") }
    var op4 by remember { mutableStateOf("") }
    var resp by remember { mutableStateOf("") }
    var confirmarEliminarId by remember { mutableStateOf<Int?>(null) }

    fun abrirAgregar() {
        editando = Pregunta(0, 0, "", "", "", "", "", "")
        enunciado = ""; op1 = ""; op2 = ""; op3 = ""; op4 = ""; resp = ""
    }
    fun abrirEditar(p: Pregunta) {
        editando = p
        enunciado = p.pregunta
        op1 = p.opcion1.orEmpty()
        op2 = p.opcion2.orEmpty()
        op3 = p.opcion3.orEmpty()
        op4 = p.opcion4.orEmpty()
        resp = p.respuesta
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preguntas • $nombreClase") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás") }
                }
            )
        },
        floatingActionButton = {
            if (!soloLectura) {
                FloatingActionButton(onClick = { abrirAgregar() }) { Icon(Icons.Filled.Add, contentDescription = "Agregar") }
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            // ====== MODO ALUMNO ======
            if (soloLectura) {
                if (vm.yaRespondido) {
                    vm.calificacionGuardada?.let {
                        AssistChip(onClick = { }, label = { Text("Ya respondiste • Calificación: $it%") })
                    } ?: AssistChip(onClick = { }, label = { Text("Ya respondiste este cuestionario") })
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.preguntas, key = { it.id }) { p ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                val textoLimpio = Html.fromHtml(
                                    p.pregunta, Html.FROM_HTML_MODE_LEGACY
                                ).toString()

                                Text(textoLimpio, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))

                                // opciones mostradas solo si no está bloqueado
                                val opciones = listOfNotNull(
                                    p.opcion1?.takeIf { it.isNotBlank() }?.let { "A) " to it },
                                    p.opcion2?.takeIf { it.isNotBlank() }?.let { "B) " to it },
                                    p.opcion3?.takeIf { it.isNotBlank() }?.let { "C) " to it },
                                    p.opcion4?.takeIf { it.isNotBlank() }?.let { "D) " to it }
                                )

                                opciones.forEach { (pref, texto) ->
                                    val elegido = vm.respuestas[p.id] == texto
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        RadioButton(
                                            selected = elegido,
                                            onClick = if (vm.yaRespondido) null else {
                                                { vm.marcarRespuesta(p.id, texto) }
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("$pref$texto", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // Tras enviar, si falló, muestra la correcta
                                if (vm.mostrarSoluciones) {
                                    val elegida = vm.respuestas[p.id]
                                    val ok = elegida?.trim().equals(p.respuesta.trim(), true)
                                    if (!ok) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Respuesta correcta: ${p.respuesta}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { vm.enviarRespuestas { /* nada extra, ya se bloquea */ } },
                    enabled = !vm.yaRespondido && !vm.enviando && vm.preguntas.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (vm.enviando) "Enviando..." else "Enviar") }
            }
            // ====== MODO PROFESOR (igual que ya tenías) ======
            else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.preguntas, key = { it.id }) { p ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                val textoLimpio = Html.fromHtml(
                                    p.pregunta, Html.FROM_HTML_MODE_LEGACY
                                ).toString()

                                Text(textoLimpio, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                listOfNotNull(
                                    p.opcion1?.takeIf { it.isNotBlank() }?.let { "A) $it" },
                                    p.opcion2?.takeIf { it.isNotBlank() }?.let { "B) $it" },
                                    p.opcion3?.takeIf { it.isNotBlank() }?.let { "C) $it" },
                                    p.opcion4?.takeIf { it.isNotBlank() }?.let { "D) $it" },
                                ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Spacer(Modifier.height(4.dp))
                                Text("Respuesta: ${p.respuesta}", style = MaterialTheme.typography.bodySmall)

                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    IconButton(onClick = { abrirEditar(p) }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                                    }
                                    IconButton(onClick = { confirmarEliminarId = p.id }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            vm.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        }
    }

    // ===== Diálogo agregar/editar (solo profesor) =====
    if (!soloLectura) {
        editando?.let { p ->
            AlertDialog(
                onDismissRequest = { editando = null },
                title = { Text(if (p.id == 0) "Agregar pregunta" else "Editar pregunta") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(enunciado, { enunciado = it }, label = { Text("Enunciado") })
                        OutlinedTextField(op1, { op1 = it }, label = { Text("Opción 1 (A)") })
                        OutlinedTextField(op2, { op2 = it }, label = { Text("Opción 2 (B)") })
                        OutlinedTextField(op3, { op3 = it }, label = { Text("Opción 3 (C)") })
                        OutlinedTextField(op4, { op4 = it }, label = { Text("Opción 4 (D)") })
                        OutlinedTextField(resp, { resp = it }, label = { Text("Respuesta") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val listo = enunciado.isNotBlank() && resp.isNotBlank()
                        if (!listo) return@TextButton
                        if (p.id == 0) {
                            vm.agregarPregunta(
                                enunciado,
                                op1.ifBlank { null }, op2.ifBlank { null },
                                op3.ifBlank { null }, op4.ifBlank { null },
                                resp
                            ) { editando = null }
                        } else {
                            vm.modificarPregunta(
                                id = p.id,
                                enunciado = enunciado,
                                op1 = op1.ifBlank { null }, op2 = op2.ifBlank { null },
                                op3 = op3.ifBlank { null }, op4 = op4.ifBlank { null },
                                resp = resp
                            ) { editando = null }
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { editando = null }) { Text("Cancelar") } }
            )
        }

        confirmarEliminarId?.let { id ->
            AlertDialog(
                onDismissRequest = { confirmarEliminarId = null },
                title = { Text("Eliminar pregunta") },
                text = { Text("¿Seguro que deseas eliminar esta pregunta?") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.eliminarPregunta(id)
                        confirmarEliminarId = null
                    }) { Text("Eliminar") }
                },
                dismissButton = { TextButton(onClick = { confirmarEliminarId = null }) { Text("Cancelar") } }
            )
        }
    }
}

/* ------- Mapeos ------- */
private fun PreguntaRes.toDomain() = Pregunta(
    id, id_cuestionario, pregunta, opcion1, opcion2, opcion3, opcion4, respuesta
)
private fun List<PreguntaRes>.toDomain() = map { it.toDomain() }
