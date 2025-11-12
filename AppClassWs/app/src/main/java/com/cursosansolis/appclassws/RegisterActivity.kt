package com.cursosansolis.appclassws

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cursosansolis.appclassws.data.remote.RegisterRequest
import com.cursosansolis.appclassws.data.remote.RetrofitClient
import com.cursosansolis.appclassws.ui.theme.AppClassWsTheme
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppClassWsTheme {
                Surface(Modifier.fillMaxSize()) {
                    RegisterScreen(
                        onRegistered = { msg ->
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                            (this as Activity).finish() // regresa al login
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(onRegistered: (String) -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var email  by rememberSaveable { mutableStateOf("") }
    var pass   by rememberSaveable { mutableStateOf("") }
    var pass2  by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun doRegister() {
        if (nombre.isBlank()) { errorMsg = "Ingresa tu nombre"; return }
        if (email.isBlank())  { errorMsg = "Ingresa tu email"; return }
        if (pass.isBlank())   { errorMsg = "Ingresa una contraseña"; return }
        if (pass != pass2)    { errorMsg = "Las contraseñas no coinciden"; return }

        errorMsg = null
        loading = true

        val body = RegisterRequest(nombre = nombre, email = email, password = pass)
        RetrofitClient.api.register(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, resp: Response<ResponseBody>) {
                loading = false
                if (resp.isSuccessful) {
                    val text = resp.body()?.string()?.trim().orEmpty()
                    if (text.contains("Registro exitoso", ignoreCase = true)) {
                        onRegistered("Registro exitoso")
                    } else {
                        // Mensaje del servidor o genérico
                        errorMsg = if (text.isNotBlank()) text else "No se pudo registrar"
                    }
                } else {
                    errorMsg = "Error ${resp.code()}"
                }
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                loading = false
                errorMsg = "Error de red: ${t.localizedMessage}"
            }
        })
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Crear cuenta", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nombre, onValueChange = { nombre = it },
            label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass2, onValueChange = { pass2 = it },
            label = { Text("Confirmar contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (!errorMsg.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (!loading) doRegister() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Registrando...")
            } else {
                Text("Registrarse")
            }
        }
    }
}
