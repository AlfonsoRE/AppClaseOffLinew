@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.cursosansolis.appclassws.data.remote.AddClassRequest
import com.cursosansolis.appclassws.data.remote.RetrofitClient
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.cursosansolis.appclassws.ui.theme.ClaseModo

// 🔽 imports extra para el drawer dinámico
import com.cursosansolis.appclassws.data.remote.IdUsuarioRequest
import com.google.gson.JsonArray

class AddClassActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppClassWsTheme {

                // ===== NUEVO: estado para poblar el drawer según el usuario =====
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
                                if (r.isSuccessful) {
                                    inscritas = (r.body() ?: JsonArray()).mapNotNull { el ->
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
                                } else inscritas = emptyList()
                            }
                            override fun onFailure(c: Call<JsonArray>, t: Throwable) {
                                inscritas = emptyList()
                            }
                        })

                    // Impartidas
                    RetrofitClient.api.clasesImpartidas(IdUsuarioRequest(userId))
                        .enqueue(object : Callback<JsonArray> {
                            override fun onResponse(c: Call<JsonArray>, r: Response<JsonArray>) {
                                if (r.isSuccessful) {
                                    impartidas = (r.body() ?: JsonArray()).mapNotNull { el ->
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
                                } else impartidas = emptyList()
                            }
                            override fun onFailure(c: Call<JsonArray>, t: Throwable) {
                                impartidas = emptyList()
                            }
                        })
                }
                // ===== FIN NUEVO =====

                DrawerScaffold(
                    title = "Agregar clase",
                    onMenuItem = { item -> navigateFromDrawer(this, item) },
                    onOpenClass = { id: String, nombre: String, modo: ClaseModo -> Unit
                        startActivity(
                            Intent(this, ClassDetailActivity::class.java).apply {
                                putExtra("ID_CLASE", id)
                                putExtra("NOMBRE_CLASE", nombre)
                                putExtra("MODO_CLASE", modo)   //  enum como Serializable
                            }
                        )
                        finish()
                    },
                    inscritas = inscritas,
                    impartidas = impartidas
                ) { padding ->
                    AddClassScreen(Modifier.padding(padding))
                }

            }
        }
    }
}

@Composable
private fun AddClassScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
    val userId = prefs.getString("id", "") ?: ""

    var nombre by rememberSaveable { mutableStateOf("") }
    var materia by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var codigo by rememberSaveable { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var error   by remember { mutableStateOf<String?>(null) }
    var done    by remember { mutableStateOf(false) }

    fun submit() {
        if (userId.isBlank()) { error = "Sesión inválida"; return }
        if (nombre.isBlank() || materia.isBlank() || descripcion.isBlank() || codigo.isBlank()) {
            error = "Completa todos los campos"; return
        }
        error = null; loading = true

        val body = AddClassRequest(
            nombre = nombre,
            materia = materia,
            descripcion = descripcion,
            codigo = codigo,
            idUsuario = userId
        )

        RetrofitClient.api.addClass(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, resp: Response<ResponseBody>) {
                loading = false
                if (resp.isSuccessful) {
                    done = true
                    Toast.makeText(ctx, "Clase creada", Toast.LENGTH_SHORT).show()
                } else error = "Error ${resp.code()}"
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                loading = false
                error = "Red: ${t.localizedMessage}"
            }
        })
    }

    if (done) (ctx as? ComponentActivity)?.finish()

    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = nombre, onValueChange = { nombre = it },
            label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = materia, onValueChange = { materia = it },
            label = { Text("Materia") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = descripcion, onValueChange = { descripcion = it },
            label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = codigo, onValueChange = { codigo = it },
            label = { Text("Código") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { if (!loading) submit() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Guardando..." else "Crear clase")
        }
    }
}
