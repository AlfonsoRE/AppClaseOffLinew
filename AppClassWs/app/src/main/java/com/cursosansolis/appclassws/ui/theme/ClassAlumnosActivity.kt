@file:OptIn(ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import com.cursosansolis.appclassws.data.remote.IdUsuarioRequest
import com.cursosansolis.appclassws.ui.theme.ClaseModo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursosansolis.appclassws.data.remote.IdClaseRequest
import com.cursosansolis.appclassws.data.remote.IdRelacionRequest
import com.cursosansolis.appclassws.data.remote.RetrofitClient
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import android.util.Log
import com.cursosansolis.appclassws.data.remote.IdIntRequest



class ClassAlumnosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idClase = intent?.getStringExtra("ID_CLASE") ?: ""
        val modo = intent?.getStringExtra("MODO_CLASE")
            ?.let { runCatching { ClaseModo.valueOf(it) }.getOrNull() }
            ?: ClaseModo.IMPARTIDAS   // por defecto, como dueño

        setContent {
            AppClassWsTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("Alumnos") }) }) { p ->
                    AlumnosScreen(
                        modifier = Modifier.padding(p),
                        idClase = idClase,
                        modo = modo
                    )
                }
            }
        }
    }
}

@Composable
private fun AlumnosScreen(
    modifier: Modifier = Modifier,
    idClase: String,
    modo: ClaseModo               //  NUEVO
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var alumnos by remember { mutableStateOf(listOf<JsonObject>()) }
    var maestro by remember { mutableStateOf<String?>(null) }

    // cargar alumnos
    LaunchedEffect(idClase) {
        RetrofitClient.api.alumnosPorClase(IdClaseRequest(idClase))
            .enqueue(object : Callback<JsonArray> {
                override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                    loading = false
                    if (resp.isSuccessful) {
                        alumnos = resp.body()?.mapNotNull { it.asJsonObject } ?: emptyList()
                    } else error = "Error ${resp.code()}"
                }
                override fun onFailure(call: Call<JsonArray>, t: Throwable) {
                    loading = false; error = "Red: ${t.localizedMessage}"
                }
            })

        // cargar maestro
        loadMaestroNombre(idClase) { nombre -> maestro = nombre }
    }

    Column(modifier.padding(16.dp)) {
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            else -> {
                // Maestro
                Text("Maestro", style = MaterialTheme.typography.titleMedium)
                Divider(Modifier.padding(vertical = 4.dp))
                Text(maestro ?: "—", style = MaterialTheme.typography.bodyLarge)

                Spacer(Modifier.height(16.dp))

                // Alumnos
                Text("Alumnos", style = MaterialTheme.typography.titleMedium)
                Divider(Modifier.padding(vertical = 4.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(alumnos) { row ->
                        val idRelacion = row.get("id")?.asString.orEmpty()
                        val nombre = row.get("nombre")?.asString ?: "Sin nombre"

                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(nombre, style = MaterialTheme.typography.titleMedium)

                                // 👇 SOLO en IMPARTIDAS mostramos el botón
                                if (modo == ClaseModo.IMPARTIDAS) {
                                    TextButton(
                                        onClick = {
                                            // seguridad extra: no ejecutes si no es IMPARTIDAS
                                            if (modo != ClaseModo.IMPARTIDAS) return@TextButton
                                            RetrofitClient.api.expulsarAlumno(
                                                IdRelacionRequest(idRelacion)
                                            ).enqueue(object : Callback<ResponseBody> {
                                                override fun onResponse(
                                                    call: Call<ResponseBody>,
                                                    resp: Response<ResponseBody>
                                                ) {
                                                    if (resp.isSuccessful) {
                                                        alumnos = alumnos.filterNot {
                                                            it.get("id")?.asString == idRelacion
                                                        }
                                                    }
                                                }
                                                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
                                            })
                                        }
                                    ) { Text("Expulsar") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun loadMaestroNombre(
    idClase: String,
    onResult: (String?) -> Unit
) {
    val idClaseInt = idClase.toIntOrNull()
    if (idClaseInt == null) {
        Log.e("MAESTRO", "idClase inválido: $idClase")
        onResult(null)
        return
    }

    // 1) Clase por ID -> obtener id_usuario
    RetrofitClient.api.clasePorId(IdIntRequest(idClaseInt))
        .enqueue(object : Callback<JsonArray> {
            override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                if (!resp.isSuccessful) {
                    Log.e("MAESTRO", "clasePorId HTTP ${resp.code()}")
                    onResult(null); return
                }

                val clase = resp.body()?.firstOrNull()?.asJsonObject
                Log.d("MAESTRO", "clasePorId -> ${resp.body()}")
                val idUsuario = clase?.get("id_usuario")?.asInt
                if (idUsuario == null) {
                    Log.e("MAESTRO", "id_usuario nulo en respuesta")
                    onResult(null); return
                }

                // 2) Usuario por id -> nombre
                RetrofitClient.api.usuarioPorId(IdIntRequest(idUsuario))
                    .enqueue(object : Callback<JsonArray> {
                        override fun onResponse(call2: Call<JsonArray>, resp2: Response<JsonArray>) {
                            if (!resp2.isSuccessful) {
                                Log.e("MAESTRO", "usuarioPorId HTTP ${resp2.code()}")
                                onResult(null); return
                            }
                            Log.d("MAESTRO", "usuarioPorId -> ${resp2.body()}")
                            val nombre = resp2.body()
                                ?.firstOrNull()
                                ?.asJsonObject
                                ?.get("nombre")
                                ?.asString
                            onResult(nombre)
                        }

                        override fun onFailure(call2: Call<JsonArray>, t: Throwable) {
                            Log.e("MAESTRO", "usuarioPorId error", t)
                            onResult(null)
                        }
                    })
            }

            override fun onFailure(call: Call<JsonArray>, t: Throwable) {
                Log.e("MAESTRO", "clasePorId error", t)
                onResult(null)
            }
        })
}
