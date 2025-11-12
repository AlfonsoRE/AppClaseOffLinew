@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cursosansolis.appclassws.data.remote.IdUsuarioRequest
import com.cursosansolis.appclassws.data.remote.RetrofitClient
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme
import com.cursosansolis.appclassws.ui.theme.ClaseModo     // 👈 enum en ui.theme
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class InscritasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppClassWsTheme {
                // --- poblar drawer ---
                val prefs = getSharedPreferences("session", Context.MODE_PRIVATE)
                val userId = remember { prefs.getString("id", "") ?: "" }

                var inscritas by remember { mutableStateOf<List<DrawerClase>>(emptyList()) }
                var impartidas by remember { mutableStateOf<List<DrawerClase>>(emptyList()) }

                LaunchedEffect(userId) {
                    if (userId.isBlank()) return@LaunchedEffect

                    // Inscritas
                    RetrofitClient.api.clasesInscritas(IdUsuarioRequest(userId))
                        .enqueue(object : Callback<JsonArray> {
                            override fun onResponse(c: Call<JsonArray>, r: Response<JsonArray>) {
                                inscritas = if (r.isSuccessful) {
                                    (r.body() ?: JsonArray()).mapNotNull { el ->
                                        val o = el.asJsonObject
                                        val id = o.get("id_clase")?.asString
                                            ?: o.get("idClase")?.asString
                                            ?: o.get("id")?.asString
                                            ?: return@mapNotNull null
                                        val nombre = o.get("materia")?.asString
                                            ?: o.get("nombre")?.asString
                                            ?: o.get("titulo")?.asString
                                            ?: "Sin nombre"
                                        DrawerClase(id, nombre)
                                    }
                                } else emptyList()
                            }
                            override fun onFailure(c: Call<JsonArray>, t: Throwable) {
                                inscritas = emptyList()
                            }
                        })

                    // Impartidas (para el drawer)
                    RetrofitClient.api.clasesImpartidas(IdUsuarioRequest(userId))
                        .enqueue(object : Callback<JsonArray> {
                            override fun onResponse(c: Call<JsonArray>, r: Response<JsonArray>) {
                                impartidas = if (r.isSuccessful) {
                                    (r.body() ?: JsonArray()).mapNotNull { el ->
                                        val o = el.asJsonObject
                                        val id = o.get("id_clase")?.asString
                                            ?: o.get("idClase")?.asString
                                            ?: o.get("id")?.asString
                                            ?: return@mapNotNull null
                                        val nombre = o.get("materia")?.asString
                                            ?: o.get("nombre")?.asString
                                            ?: o.get("titulo")?.asString
                                            ?: "Sin nombre"
                                        DrawerClase(id, nombre)
                                    }
                                } else emptyList()
                            }
                            override fun onFailure(c: Call<JsonArray>, t: Throwable) {
                                impartidas = emptyList()
                            }
                        })
                }

                DrawerScaffold(
                    title = "Clases inscritas",
                    onMenuItem = { item -> navigateFromDrawer(this, item) },
                    // 👇 Tipos explícitos si te daba "Cannot infer a type..."
                    onOpenClass = { id: String, nombre: String, modo: ClaseModo ->
                        startActivity(
                            Intent(this, ClassDetailActivity::class.java).apply {
                                putExtra("ID_CLASE", id)
                                putExtra("NOMBRE_CLASE", nombre)
                                putExtra("MODO_CLASE", ClaseModo.INSCRITAS.name) // alumno

                            }
                        )
                        finish()
                    },
                    inscritas = inscritas,
                    impartidas = impartidas
                ) { padding ->
                    InscritasScreen(padding)
                }
            }
        }
    }
}

@Composable
private fun InscritasScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    val userId = remember {
        ctx.getSharedPreferences("session", Context.MODE_PRIVATE).getString("id", "") ?: ""
    }

    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }
    var filas   by remember { mutableStateOf(listOf<JsonObject>()) }

    LaunchedEffect(userId) {
        if (userId.isBlank()) { error = "Sesión inválida"; loading = false; return@LaunchedEffect }
        RetrofitClient.api.clasesInscritas(IdUsuarioRequest(userId))
            .enqueue(object : Callback<JsonArray> {
                override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                    loading = false
                    if (resp.isSuccessful) {
                        filas = resp.body()?.mapNotNull { it.asJsonObject } ?: emptyList()
                    } else error = "Error ${resp.code()}"
                }
                override fun onFailure(call: Call<JsonArray>, t: Throwable) {
                    loading = false; error = "Red: ${t.localizedMessage}"
                }
            })
    }

    Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filas) { obj -> ClaseCardInscrita(obj) }
            }
        }
    }
}

@Composable
private fun ClaseCardInscrita(obj: JsonObject) {
    val ctx = LocalContext.current

    val idClase = obj.get("id_clase")?.asString
        ?: obj.get("idClase")?.asString
        ?: obj.get("id")?.asString
        ?: ""

    val tituloVisible = obj.get("materia")?.asString
        ?: obj.get("nombre")?.asString
        ?: obj.get("titulo")?.asString
        ?: "Sin nombre"

    val desc = obj.get("descripcion")?.asString ?: ""
    val maestro = obj.get("maestro")?.asString
        ?: obj.get("profesor")?.asString
        ?: obj.get("docente")?.asString
        ?: ""

    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(tituloVisible, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodyMedium)
                if (maestro.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Maestro: $maestro", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(
                enabled = idClase.isNotBlank(),
                onClick = {
                    ctx.startActivity(
                        Intent(ctx, ClassDetailActivity::class.java).apply {
                            putExtra("ID_CLASE", idClase)
                            putExtra("NOMBRE_CLASE", tituloVisible)
                            putExtra("MODO_CLASE", ClaseModo.INSCRITAS.name) //  String
                        }
                    )
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ir")
            }
        }
    }
}
