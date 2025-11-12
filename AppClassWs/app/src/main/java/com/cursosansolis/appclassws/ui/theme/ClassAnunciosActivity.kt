@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme
import com.cursosansolis.appclassws.ui.theme.ClaseModo

class ClassAnunciosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extras que enviaste desde ClassDetailActivity
        val idClase = intent?.getStringExtra("ID_CLASE") ?: ""
        val nombre  = intent?.getStringExtra("NOMBRE_CLASE") ?: ""
        val codigo  = intent?.getStringExtra("CODIGO_CLASE") ?: ""

        // ✅ Lee el modo como enum (o como String.name si así lo mandaste); fallback seguro a INSCRITAS
        val modo: ClaseModo = getModoClaseExtra(intent)
            ?: intent?.getStringExtra("MODO_CLASE")?.let { runCatching { ClaseModo.valueOf(it) }.getOrNull() }
            ?: ClaseModo.INSCRITAS

        // Id del usuario logueado (para permisos en AnunciosScreen)
        val userId = getSharedPreferences("session", Context.MODE_PRIVATE)
            .getString("id", "") ?: ""

        setContent {
            AppClassWsTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Anuncios") }) }
                ) { p ->
                    AnunciosScreen(
                        modifier    = Modifier.padding(p),
                        idClase     = idClase,
                        nombreClase = nombre,
                        codigoClase = codigo,
                        modo        = modo,     // 👈 respeta el modo recibido
                        userId      = userId    // 👈 id de sesión
                    )
                }
            }
        }
    }
}

/** Helper para leer el enum de forma segura en todas las APIs */
private fun getModoClaseExtra(intent: Intent?): ClaseModo? {
    if (intent == null) return null
    return if (Build.VERSION.SDK_INT >= 33) {
        intent.getSerializableExtra("MODO_CLASE", ClaseModo::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getSerializableExtra("MODO_CLASE") as? ClaseModo
    }
}
