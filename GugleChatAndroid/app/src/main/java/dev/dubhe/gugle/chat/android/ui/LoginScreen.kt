package dev.dubhe.gugle.chat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    initialBackendUrl: String,
    onLogin: (String, String, String) -> Unit,
    onNavigateRegister: () -> Unit,
    error: String?
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var backendUrl by remember { mutableStateOf(initialBackendUrl) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GugleChat", style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "✕" else "⚙") }
        }
        Spacer(Modifier.height(16.dp))

        if (showSettings) {
            OutlinedTextField(backendUrl, { backendUrl = it },
                label = { Text("Backend URL") },
                placeholder = { Text("http://server:3250") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(username, { username = it }, label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error != null) { Text(error, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button({ onLogin(username, password, backendUrl.trim().trimEnd('/')) }, Modifier.fillMaxWidth()) { Text("Sign In") }
        TextButton({ onNavigateRegister() }) { Text("Don't have an account? Register") }
    }
}

@Composable
fun RegisterScreen(
    initialBackendUrl: String,
    onRegister: (String, String, String, String) -> Unit,
    onNavigateLogin: () -> Unit,
    error: String?
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var backendUrl by remember { mutableStateOf(initialBackendUrl) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Register", style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "✕" else "⚙") }
        }
        Spacer(Modifier.height(16.dp))

        if (showSettings) {
            OutlinedTextField(backendUrl, { backendUrl = it },
                label = { Text("Backend URL") },
                placeholder = { Text("http://server:3250") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(username, { username = it }, label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error != null) { Text(error, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button({ onRegister(username, email, password, backendUrl.trim().trimEnd('/')) }, Modifier.fillMaxWidth()) { Text("Register") }
        TextButton({ onNavigateLogin() }) { Text("Already have an account? Sign In") }
    }
}
