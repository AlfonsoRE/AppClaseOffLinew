@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cursosansolis.appclassws

import android.app.Activity
import android.content.Context
import com.cursosansolis.appclassws.ui.theme.ClaseModo

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// AppDrawer.kt
data class DrawerClase(val id: String, val nombre: String)

@Composable
fun DrawerScaffold(
    title: String,
    onMenuItem: (MenuItem) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},

    onOpenClass: (String, String, ClaseModo) -> Unit = { _, _, _ -> },
    inscritas: List<DrawerClase> = emptyList(),   //  dinámico
    impartidas: List<DrawerClase> = emptyList(),  //  dinámico
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Text("Menú", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))

                NavigationDrawerItem(
                    label = { Text("Dashboard") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onMenuItem(MenuItem.Dashboard) }
                )

                if (inscritas.isNotEmpty()) {
                    Divider()
                    Text(
                        "Clases inscritas",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            .clickable {
                                scope.launch { drawerState.close() }
                                onMenuItem(MenuItem.Inscritas) // listado principal
                            }
                    )
                    inscritas.forEach { c ->
                        Text(
                            c.nombre,
                            modifier = Modifier
                                .padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    onOpenClass(c.id, c.nombre, ClaseModo.INSCRITAS)
                                }
                        )
                    }
                }

                if (impartidas.isNotEmpty()) {
                    Divider()
                    Text(
                        "Clases impartidas",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            .clickable {
                                scope.launch { drawerState.close() }
                                onMenuItem(MenuItem.Impartidas) // listado principal
                            }
                    )
                    impartidas.forEach { c ->
                        Text(
                            c.nombre,
                            modifier = Modifier
                                .padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    onOpenClass(c.id, c.nombre, ClaseModo.IMPARTIDAS)
                                }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
                        }
                    },
                    actions = actions
                )
            }
        ) { inner -> content(inner) }
    }
}

enum class MenuItem { Dashboard, Impartidas, Inscritas }

// Opcional: helper de navegación (déjalo en este archivo o en cualquiera del mismo package)
fun navigateFromDrawer(activity: Activity, item: MenuItem) {
    when (item) {
        MenuItem.Dashboard  -> activity.startActivity(Intent(activity, DashboardActivity::class.java))
        MenuItem.Impartidas -> activity.startActivity(Intent(activity, ImpartidasActivity::class.java))
        MenuItem.Inscritas  -> activity.startActivity(Intent(activity, InscritasActivity::class.java))
    }
}
