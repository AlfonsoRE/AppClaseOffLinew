@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import com.cursosansolis.appclassws.ui.theme.ClaseModo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background // NUEVO
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape // NUEVO
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // NUEVO
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme

import com.cursosansolis.appclassws.data.remote.IdUsuarioRequest
import com.cursosansolis.appclassws.data.remote.IdIntRequest // NUEVO
import com.cursosansolis.appclassws.data.remote.RetrofitClient
import com.google.gson.JsonArray
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppClassWsTheme {

                // === Estado sesión ===
                val prefs = getSharedPreferences("session", Context.MODE_PRIVATE)
                val userId = remember { prefs.getString("id", "") ?: "" }

                // === NUEVO: nombre del usuario logueado ===
                var nombre by remember { mutableStateOf<String?>(null) }
                var cargandoNombre by remember { mutableStateOf(false) }
                LaunchedEffect(userId) {
                    val uid = userId.toIntOrNull() ?: return@LaunchedEffect
                    cargandoNombre = true
                    runCatching {
                        RetrofitClient.api.consultarUsuario(IdIntRequest(uid))
                    }.onSuccess { arr ->
                        nombre = arr.firstOrNull()?.nombre?.trim().takeUnless { it.isNullOrBlank() }
                    }
                    cargandoNombre = false
                }

                // === Listas de clases ===
                var inscritas by remember { mutableStateOf<List<DrawerClase>>(emptyList()) }
                var impartidas by remember { mutableStateOf<List<DrawerClase>>(emptyList()) }

                // Carga asíncrona (una vez por usuario)
                LaunchedEffect(userId) {
                    if (userId.isBlank()) return@LaunchedEffect

                    // Inscritas
                    RetrofitClient.api.clasesInscritas(IdUsuarioRequest(userId))
                        .enqueue(object : Callback<JsonArray> {
                            override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                                if (resp.isSuccessful) {
                                    inscritas = (resp.body() ?: JsonArray()).mapNotNull { el ->
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
                                } else {
                                    inscritas = emptyList()
                                }
                            }
                            override fun onFailure(call: Call<JsonArray>, t: Throwable) {
                                inscritas = emptyList()
                            }
                        })

                    // Impartidas
                    RetrofitClient.api.clasesImpartidas(IdUsuarioRequest(userId))
                        .enqueue(object : Callback<JsonArray> {
                            override fun onResponse(call: Call<JsonArray>, resp: Response<JsonArray>) {
                                if (resp.isSuccessful) {
                                    impartidas = (resp.body() ?: JsonArray()).mapNotNull { el ->
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
                                } else {
                                    impartidas = emptyList()
                                }
                            }
                            override fun onFailure(call: Call<JsonArray>, t: Throwable) {
                                impartidas = emptyList()
                            }
                        })
                }

                DrawerScaffold(
                    title = "Dashboard",
                    onMenuItem = { item -> navigateFromDrawer(this, item) },
                    actions = {
                        // ======= NUEVO: avatar + nombre junto a "Salir" =======
                        val inicial = nombre?.firstOrNull()?.uppercaseChar()
                        if (inicial != null) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = inicial.toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        when {
                            cargandoNombre -> {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            nombre != null -> {
                                Text(
                                    text = nombre!!,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            else -> {
                                Text(
                                    text = "Invitado",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                        // =======================================================

                        val ctx = this@DashboardActivity
                        TextButton(onClick = {
                            val p = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
                            p.edit().clear().apply()
                            val i = Intent(ctx, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(i)
                            finish()
                        }) { Text("Salir") }
                    },
                    onOpenClass = { id: String, nombre: String, modo: ClaseModo ->
                        startActivity(
                            Intent(this, ClassDetailActivity::class.java).apply {
                                putExtra("ID_CLASE", id)
                                putExtra("NOMBRE_CLASE", nombre)
                                putExtra("MODO_CLASE", modo.name)   // enviamos como String
                            }
                        )
                    },
                    inscritas = inscritas,
                    impartidas = impartidas
                ) { padding ->
                    DashboardScreen(Modifier.padding(padding))
                }

            }
        }
    }
}

@Composable
private fun DashboardScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { ctx.startActivity(Intent(ctx, AddClassActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Agregar clase") }

        Button(
            onClick = { ctx.startActivity(Intent(ctx, JoinClassActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Unirse a una clase") }

        OutlinedButton(
            onClick = { ctx.startActivity(Intent(ctx, ImpartidasActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ver clases impartidas") }

        OutlinedButton(
            onClick = { ctx.startActivity(Intent(ctx, InscritasActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ver clases inscritas") }
    }
}
