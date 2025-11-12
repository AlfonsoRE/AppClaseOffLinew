@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cursosansolis.appclassws

import com.cursosansolis.appclassws.ui.theme.CalificacionesActivity

import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme
import com.cursosansolis.appclassws.ui.theme.ClaseModo
import java.io.Serializable

class ClassDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idClase  = intent?.getStringExtra("ID_CLASE") ?: ""
        val nombre   = intent?.getStringExtra("NOMBRE_CLASE") ?: "Sin nombre"

        //  Lee el modo que te mandaron (enum o String.name). Si no viene, usa INSCRITAS.
        val modo: ClaseModo = getModoClaseExtra(intent)
            ?: intent?.getStringExtra("MODO_CLASE")?.let { runCatching { ClaseModo.valueOf(it) }.getOrNull() }
            ?: ClaseModo.INSCRITAS

        setContent {
            AppClassWsTheme {
                // Si usas DrawerScaffold con listas reales, pásalas aquí. Para ser concisos, lo dejamos vacío.
                DrawerScaffold(
                    title = "Menú de clase",
                    onMenuItem = { item -> navigateFromDrawer(this, item) },
                    onOpenClass = { id: String, nom: String, modoClase: ClaseModo ->
                        startActivity(
                            Intent(this, ClassDetailActivity::class.java).apply {
                                putExtra("ID_CLASE", id)
                                putExtra("NOMBRE_CLASE", nom)
                                // Enviamos ambas variantes por robustez
                                putExtra("MODO_CLASE", modoClase as Serializable)
                                putExtra("MODO_CLASE", modoClase.name)
                            }
                        )
                        finish()
                    },
                    inscritas = emptyList(),
                    impartidas = emptyList()
                ) { padding ->
                    ClassDetailScreen(
                        idClase = idClase,
                        nombreClase = nombre,
                        modo = modo,
                        modifier = Modifier.padding(padding)
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

@Composable
private fun ClassDetailScreen(
    idClase: String,
    nombreClase: String,
    modo: ClaseModo,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Clase: $nombreClase", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = {
                ctx.startActivity(
                    Intent(ctx, ClassAlumnosActivity::class.java).apply {
                        putExtra("ID_CLASE", idClase)
                        putExtra("NOMBRE_CLASE", nombreClase)
                        // Enviamos enum y String.name (soporta ambos lectores)
                        putExtra("MODO_CLASE", modo as Serializable)
                        putExtra("MODO_CLASE", modo.name)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Alumnos") }

        Button(
            onClick = {
                ctx.startActivity(
                    Intent(ctx, ClassAnunciosActivity::class.java).apply {
                        putExtra("ID_CLASE", idClase)
                        putExtra("NOMBRE_CLASE", nombreClase)
                        putExtra("CODIGO_CLASE", "1234")
                        putExtra("MODO_CLASE", modo as Serializable)
                        putExtra("MODO_CLASE", modo.name)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Anuncios") }

        Button(
            onClick = {
                ctx.startActivity(
                    Intent(ctx, ContenidoActivity::class.java).apply {
                        putExtra("ID_CLASE", idClase)
                        putExtra("NOMBRE_CLASE", nombreClase)
                        putExtra("MODO_CLASE", modo as Serializable)
                        putExtra("MODO_CLASE", modo.name)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Contenido") }
        if (modo == ClaseModo.INSCRITAS) {
            Button(
                onClick = {
                    // Abrir CalificacionesActivity solo para alumnos (INSCRITAS)
                    ctx.startActivity(
                        Intent(ctx, CalificacionesActivity::class.java).apply {
                            putExtra("ID_CLASE", idClase)
                            putExtra("NOMBRE_CLASE", nombreClase)
                            // Mandamos ambas variantes del modo para mantener compatibilidad
                            putExtra("MODO_CLASE", modo as java.io.Serializable)
                            putExtra("MODO_CLASE", modo.name)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Calificaciones") }
        }
    }
    }