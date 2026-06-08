package dev.dubhe.gugle.chat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dubhe.gugle.chat.android.ui.LoginScreen
import dev.dubhe.gugle.chat.android.ui.MainScreen
import dev.dubhe.gugle.chat.android.ui.RegisterScreen
import dev.dubhe.gugle.chat.android.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("guglechat", 0)
        val savedBackendUrl = prefs.getString("backend_url", "") ?: ""

        setContent {
            val viewModel: ChatViewModel = viewModel()
            val loggedIn by viewModel.loggedIn.collectAsState()
            var showRegister by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }

            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF313338), surface = Color(0xFF1E1F22),
                primary = Color(0xFF5865F2), onPrimary = Color.White
            )) {
                Surface(Modifier.fillMaxSize()) {
                    if (loggedIn) {
                        MainScreen(viewModel) { viewModel.logout() }
                    } else if (showRegister) {
                        RegisterScreen(
                            initialBackendUrl = savedBackendUrl,
                            onRegister = { u, e, p, url ->
                                viewModel.register(u, e, p, url) { error = it }
                            },
                            onNavigateLogin = { showRegister = false },
                            error = error
                        )
                    } else {
                        LoginScreen(
                            initialBackendUrl = savedBackendUrl,
                            onLogin = { u, p, url -> viewModel.login(u, p, url) { error = it } },
                            onNavigateRegister = { showRegister = true },
                            error = error
                        )
                    }
                }
            }
        }
    }
}
