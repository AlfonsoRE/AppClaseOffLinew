package com.cursosansolis.appclassws

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.viewModelScope
import com.cursosansolis.appclassws.data.remote.*
import kotlinx.coroutines.launch
import okhttp3.ResponseBody

class TemaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idClaseStr  = intent.getStringExtra("ID_CLASE") ?: "0"  // ← String (para tus requests)
        val nombreClase = intent.getStringExtra("NOMBRE_CLASE") ?: "Sin nombre"
        val idClaseInt  = idClaseStr.toIntOrNull() ?: 0             // ← Int (para guardar/modificar)

        // SIN compose-viewmodel: creamos el VM con ViewModelProvider
        val vm = ViewModelProvider(this, TemaVMFactory(idClaseInt, idClaseStr))
            .get(TemaViewModel::class.java)

        setContent {
            MaterialTheme {
                TemaScreen(
                    nombreClase = nombreClase,
                    vm = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}

/* -------------------- ViewModel -------------------- */

class TemaVMFactory(
    private val idClase: Int,
    private val idClaseStr: String
) : Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TemaViewModel(idClase, idClaseStr) as T
    }
}

class TemaViewModel(
    private val idClase: Int,        // para Guardar/Modificar (tu PHP espera int)
    private val idClaseStr: String   // para Listar (tu IdClaseRequest es String)
) : ViewModel() {

    var temas by mutableStateOf<List<Tema>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val api = RetrofitClient.api

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        loading = true; error = null
        runCatching { api.consultarTemasClase(IdClaseRequest(idClaseStr)) }   // ← String
            .onSuccess { temas = it }
            .onFailure { error = it.message ?: "Error desconocido" }
        loading = false
    }

    fun crearTema(titulo: String) = viewModelScope.launch {
        loading = true; error = null
        runCatching<ResponseBody> {
            api.guardarTema(GuardarTemaRequest(titulo = titulo, id_clase = idClase)) // ← Int
        }.onSuccess { refresh() }
            .onFailure { error = it.message ?: "No se pudo guardar" }
        loading = false
    }

    fun eliminarTema(idTema: Int) = viewModelScope.launch {
        loading = true; error = null
        runCatching<ResponseBody> {
            api.eliminarTema(IdOnlyRequest(idTema.toString()))  // ← tu IdOnlyRequest es String
        }.onSuccess { refresh() }
            .onFailure { error = it.message ?: "No se pudo eliminar" }
        loading = false
    }

    fun modificarTema(id: Int, nuevoTitulo: String) = viewModelScope.launch {
        loading = true; error = null
        runCatching<ResponseBody> {
            api.modificarTema(ModificarTemaRequest(id = id, titulo = nuevoTitulo, id_clase = idClase)) // ← Int
        }.onSuccess { refresh() }
            .onFailure { error = it.message ?: "No se pudo modificar" }
        loading = false
    }
}

/* -------------------- UI -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemaScreen(
    nombreClase: String,
    vm: TemaViewModel,
    onBack: () -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tema • $nombreClase") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo tema")
            }
        }
    ) { innerPadding ->
        // pantalla minimal: solo mensaje y FAB para crear
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Los temas y sus tareas se ven en Contenido.\nUsa el botón + para agregar más.")
        }
    }

    // Diálogo: agregar tema
    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("Agregar tema") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Título del tema") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        vm.crearTema(newTitle.trim())
                        newTitle = ""
                        showNew = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton({ showNew = false }) { Text("Cancelar") } }
        )
    }
}
