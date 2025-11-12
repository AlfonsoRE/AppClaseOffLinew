



@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws.ui.theme

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cursosansolis.appclassws.data.remote.*
import kotlinx.coroutines.launch

private fun String.stripHtml(): String =
    HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

class CalificacionesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idClaseStr  = intent.getStringExtra("ID_CLASE") ?: "0"
        val nombreClase = intent.getStringExtra("NOMBRE_CLASE") ?: "Clase"

        val prefs = getSharedPreferences("session", Context.MODE_PRIVATE)
        val userIdStr = prefs.getString("id", "") ?: ""
        val userId = userIdStr.toIntOrNull() ?: 0

        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CalificacionesVM(idClaseStr, userId) as T
            }
        })[CalificacionesVM::class.java]

        setContent {
            MaterialTheme {
                CalificacionesScreen(
                    nombreClase = nombreClase,
                    vm = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}

/* -------------------- ViewModel -------------------- */

data class GradeRow(
    val titulo: String,
    val fecha: String,
    val calificacion: String
)

class CalificacionesVM(
    private val idClaseStr: String,
    private val userId: Int
) : ViewModel() {

    private val api = RetrofitClient.api

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    var tareasRows by mutableStateOf<List<GradeRow>>(emptyList()); private set
    var cuestRows  by mutableStateOf<List<GradeRow>>(emptyList()); private set

    init { cargarTodo() }

    fun cargarTodo() = viewModelScope.launch {
        loading = true; error = null
        tareasRows = emptyList(); cuestRows = emptyList()

        runCatching {
            // 1) TAREAS de la clase
            val tareas = api.consultarTareasClases(IdClaseRequest(idClaseStr))

            val tareasMine = buildList {
                for (t in tareas) {
                    val hist = api.consultarHistorialTareasPorTarea(IdTareasRequest(t.id))
                        .filter { it.id_usuario == userId }

                    if (hist.isNotEmpty()) {
                        // me quedo con la más reciente por fecha o por id
                        val sel = hist.maxByOrNull { it.fecha ?: "" } ?: hist.last()
                        add(
                            GradeRow(
                                titulo = t.titulo.stripHtml(),
                                fecha = (sel.fecha ?: "").trim(),
                                calificacion = (sel.calificacion ?: "").trim().ifBlank { "-" }
                            )
                        )
                    }
                }
            }

            // 2) CUESTIONARIOS de la clase (vía temas)
            val temas = api.consultarTemasClase(IdClaseRequest(idClaseStr))
            val cuestMine = buildList {
                for (tema in temas) {
                    val cuests: List<CuestionarioRes> =
                        api.consultarCuestionarioPorTema(IdTemaRequest(tema.id))

                    for (c in cuests) {
                        val hist = api.consultarHistorialCuestionarioPorCuestionario(
                            IdCuestionarioRequest(c.id)
                        ).firstOrNull { it.id_usuario == userId }

                        if (hist != null) {
                            add(
                                GradeRow(
                                    titulo = c.titulo.stripHtml(),
                                    fecha = hist.fecha.trim(),
                                    calificacion = hist.calificacion.trim().ifBlank { "-" }
                                )
                            )
                        }
                    }
                }
            }

            tareasRows = tareasMine.sortedBy { it.titulo.lowercase() }
            cuestRows  = cuestMine.sortedBy { it.titulo.lowercase() }
        }.onFailure { e ->
            error = e.localizedMessage
        }

        loading = false
    }
}

/* -------------------- UI -------------------- */

@Composable
private fun CalificacionesScreen(
    nombreClase: String,
    vm: CalificacionesVM,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calificaciones • $nombreClase") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

            // ===== TAREAS =====
            Text("Tareas", style = MaterialTheme.typography.titleMedium)
            GradeTable(rows = vm.tareasRows)

            // ===== CUESTIONARIOS =====
            Spacer(Modifier.height(8.dp))
            Text("Cuestionarios", style = MaterialTheme.typography.titleMedium)
            GradeTable(rows = vm.cuestRows)

            if (!vm.loading && vm.tareasRows.isEmpty() && vm.cuestRows.isEmpty()) {
                Text("Aún no hay calificaciones registradas para tu usuario.")
            }
        }
    }
}

@Composable
private fun GradeTable(rows: List<GradeRow>) {
    // Cabecera
    ElevatedCard {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(6.dp)) {
                Text("Título", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelLarge)
                Text("Fecha",  modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelLarge)
                Text("Calif.", modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelLarge)
            }
            Divider()
            if (rows.isEmpty()) {
                Text("— Sin registros —", modifier = Modifier.padding(8.dp))
            } else {
                LazyColumn {
                    items(rows) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                            Text(r.titulo, modifier = Modifier.weight(0.5f))
                            Text(r.fecha,  modifier = Modifier.weight(0.25f))
                            Text(r.calificacion, modifier = Modifier.weight(0.25f))
                        }
                    }
                }
            }
        }
    }
}
