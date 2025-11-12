@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cursosansolis.appclassws.data.remote.IdUsuarioRequest
import com.cursosansolis.appclassws.data.remote.JoinClassRequest
import com.cursosansolis.appclassws.data.remote.RetrofitClient
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme
import com.google.gson.JsonArray
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import com.cursosansolis.appclassws.ui.theme.ClaseModo


class JoinClassActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppClassWsTheme {

                // (Opcional) poblar el drawer dinámicamente según el usuario
                val prefs = getSharedPreferences("session", Context.MODE_PRIVATE)
                val userId = remember { prefs.getString("id", "") ?: "" }

                var drawerInscritas by remember { mutableStateOf<List<DrawerClase>>(emptyList()) }
                var drawerImpartidas by remember { mutableStateOf<List<DrawerClase>>(emptyList()) }

                LaunchedEffect(userId) {
                    if (userId.isBlank()) return@LaunchedEffect

                    // Inscritas
                    RetrofitClient.api.clasesInscritas(IdUsuarioRequest(userId))
                        .enqueue(object : Callback<JsonArray> {
                            override fun onResponse(c: Call<JsonArray>, r: Response<JsonArray>) {
                                if (r.isSuccessful) {
                                    drawerInscritas = (r.body() ?: JsonArray()).mapNotNull { el ->
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
                                } else drawerInscritas = emptyList()
                            }
                            override fun onFailure(c: Call<JsonArray>, t: Throwable) {
                                drawerInscritas = emptyList()
                            }
                        })

                    // Impartidas
                    RetrofitClient.api.clasesImpartidas(IdUsuarioRequest(userId))
                        .enqueue(object : Callback<JsonArray> {
                            override fun onResponse(c: Call<JsonArray>, r: Response<JsonArray>) {
                                if (r.isSuccessful) {
                                    drawerImpartidas = (r.body() ?: JsonArray()).mapNotNull { el ->
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
                                } else drawerImpartidas = emptyList()
                            }
                            override fun onFailure(c: Call<JsonArray>, t: Throwable) {
                                drawerImpartidas = emptyList()
                            }
                        })
                }

                DrawerScaffold(
                    title = "Unirse a una clase",
                    onMenuItem = { item -> navigateFromDrawer(this, item) },
                    onOpenClass = { id: String, nombre: String, modo: ClaseModo ->
                        startActivity(
                            Intent(this, ClassDetailActivity::class.java).apply {
                                putExtra("ID_CLASE", id)
                                putExtra("NOMBRE_CLASE", nombre)
                                putExtra("MODO_CLASE", modo.name) // se envía como String
                            }
                        )
                        finish()
                    },

                    inscritas = drawerInscritas,   // dinámicas
                    impartidas = drawerImpartidas  // dinámicas
                ) { padding ->
                    JoinClassScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun JoinClassScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
    val userId = prefs.getString("id", "") ?: ""

    var codigo  by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error   by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (userId.isBlank()) { error = "Sesión inválida"; return }
        if (codigo.isBlank()) { error = "Ingresa el código"; return }
        error = null; loading = true

        val body = JoinClassRequest(id_estudiante = userId, codigo = codigo)

        RetrofitClient.api.joinClass(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, resp: Response<ResponseBody>) {
                loading = false
                if (resp.isSuccessful) {
                    Toast.makeText(ctx, "Te uniste a la clase", Toast.LENGTH_SHORT).show()
                    // Puedes cerrar o redirigir a Inscritas:
                    // ctx.startActivity(Intent(ctx, InscritasActivity::class.java))
                    (ctx as? ComponentActivity)?.finish()
                } else error = "Error ${resp.code()}"
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                loading = false
                error = "Red: ${t.localizedMessage}"
            }
        })
    }

    // sin Scaffold interno; usamos el modifier que trae el DrawerScaffold
    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it },
            label = { Text("Código") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
            ),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { if (!loading) submit() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Uniendo..." else "Unirse a la clase")
        }
    }
}
