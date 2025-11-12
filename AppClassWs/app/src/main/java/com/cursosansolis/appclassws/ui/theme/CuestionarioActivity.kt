// CuestionarioActivity.kt
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cursosansolis.appclassws.data.remote.*
import kotlinx.coroutines.launch

// Util chiquito: quitar etiquetas HTML si vienen desde el web
private fun String.stripHtml(): String =
    HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

class CuestionarioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idClaseStr = intent.getStringExtra("ID_CLASE") ?: "0"
        val nombreClase = intent.getStringExtra("NOMBRE_CLASE") ?: "Sin nombre"
        val idClase = idClaseStr.toIntOrNull() ?: 0

        val vm = CuestionarioVM(idClaseStr, idClase)

        setContent {
            MaterialTheme {
                CrearCuestionarioScreen(
                    nombreClase = nombreClase,
                    vm = vm,
                    onCreated = { nuevoId, idTema ->
                        startActivity(
                            Intent(this, CuestionarioEditorActivity::class.java).apply {
                                putExtra("ID_CUESTIONARIO", nuevoId)
                                putExtra("ID_TEMA", idTema)
                                putExtra("ID_CLASE", idClaseStr)
                                putExtra("NOMBRE_CLASE", nombreClase)
                            }
                        )
                        finish() // Al volver, ContenidoActivity hace auto-refresh
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

/* -------------------- ViewModel -------------------- */
class CuestionarioVM(
    private val idClaseStr: String,
    private val idClase: Int
) : ViewModel() {

    private val api = RetrofitClient.api

    var temas by mutableStateOf<List<Tema>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    init { loadTemas() }

    private fun loadTemas() = viewModelScope.launch {
        loading = true; error = null
        runCatching { api.consultarTemasClase(IdClaseRequest(idClaseStr)) }
            .onSuccess { temas = it }
            .onFailure { error = it.message }
        loading = false
    }

    fun crearCuestionario(
        idTema: Int,
        titulo: String,
        descripcion: String,
        onOk: (Int) -> Unit
    ) = viewModelScope.launch {
        loading = true; error = null
        val body = GuardarCuestionarioRequest(
            id_tema = idTema,
            titulo = titulo,
            descripcion = descripcion,
            id_clase = idClase
        )
        runCatching { api.guardarCuestionario(body) }
            .onSuccess { resp -> onOk(resp.id) }
            .onFailure { error = it.message }
        loading = false
    }
}

/* -------------------- UI -------------------- */
@Composable
private fun CrearCuestionarioScreen(
    nombreClase: String,
    vm: CuestionarioVM,
    onCreated: (idCuestionario: Int, idTema: Int) -> Unit,
    onBack: () -> Unit
) {
    var idTemaSel by remember { mutableStateOf<Int?>(null) }
    var titulo by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo cuestionario • $nombreClase") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selector de tema (DropdownMenu estándar anclado al TextField)
            val temaActual = vm.temas.firstOrNull { it.id == idTemaSel }?.titulo?.stripHtml()
                ?: "Selecciona un tema"

            Box {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMenu = true },
                    readOnly = true,
                    value = temaActual,
                    onValueChange = {},
                    label = { Text("Tema") }
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    vm.temas.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.titulo.stripHtml()) },
                            onClick = {
                                idTemaSel = t.id
                                showMenu = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Descripción") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (vm.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Button(
                onClick = {
                    val tema = idTemaSel ?: return@Button
                    if (titulo.isNotBlank()) {
                        vm.crearCuestionario(
                            tema,
                            titulo.trim(),
                            desc.trim()
                        ) { nuevoId ->
                            onCreated(nuevoId, tema)
                        }
                    }
                },
                enabled = !vm.loading && (idTemaSel != null) && titulo.isNotBlank()
            ) {
                Text("Crear y agregar preguntas")
            }

            vm.error?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
