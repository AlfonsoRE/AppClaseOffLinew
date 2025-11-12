package com.cursosansolis.appclassws

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cursosansolis.appclassws.data.remote.LoginResponse
import com.cursosansolis.appclassws.data.remote.RetrofitClient
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.compose.foundation.text.KeyboardOptions


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Si ya hay sesión, entra directo
        val prefs = getSharedPreferences("session", Context.MODE_PRIVATE)
        if (prefs.getBoolean("logged", false)) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        setContent {
            AppClassWsTheme {
                Surface(Modifier.fillMaxSize()) {
                    LoginScreen()
                }
            }
        }
    }
}

@Composable
fun LoginScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun doLogin() {
        if (email.isBlank()) { errorMsg = "Ingresa tu email"; return }
        if (pass.isBlank())  { errorMsg = "Ingresa tu contraseña"; return }

        errorMsg = null
        loading = true

        RetrofitClient.api.login(email, pass).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, resp: Response<LoginResponse>) {
                loading = false
                val body = resp.body()
                if (resp.isSuccessful && body?.success == true) {
                    val prefs = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
                    with(prefs.edit()) {
                        putBoolean("logged", true)
                        putString("id", body.id)
                        putString("rol", body.rol)
                        putString("nombre", body.nombre)
                        putString("email", body.email)
                        // (opcional para auto-entrar al dashboard web la próxima vez)
                        putString("pass", pass)
                        apply()
                    }

                    Toast.makeText(ctx, "Bienvenido ${body.nombre}", Toast.LENGTH_SHORT).show()

                    ctx.startActivity(Intent(ctx, DashboardActivity::class.java))
                    (ctx as? Activity)?.finish()
                } else {
                    errorMsg = body?.message ?: "Credenciales no válidas"
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                loading = false
                errorMsg = "Error de red: ${t.localizedMessage}"
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Iniciar sesión", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (!errorMsg.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))

        // BOTÓN DE LOGIN
        Button(
            onClick = { if (!loading) doLogin() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Entrando...")
            } else {
                Text("Iniciar sesión")
            }
        }


        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { ctx.startActivity(Intent(ctx, RegisterActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("¿No tienes cuenta? Regístrate")
        }
    }
}
