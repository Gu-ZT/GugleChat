package dev.dubhe.gugle.chat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.dubhe.gugle.chat.android.data.Channel
import dev.dubhe.gugle.chat.android.data.Message
import dev.dubhe.gugle.chat.android.viewmodel.ChatViewModel

@Composable
fun MainScreen(viewModel: ChatViewModel, onLogout: () -> Unit) {
    val channels by viewModel.channels.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val current by viewModel.currentChannel.collectAsState()
    var input by remember { mutableStateOf("") }

    Row(Modifier.fillMaxSize()) {
        // Sidebar
        Column(Modifier.width(250.dp).fillMaxHeight().background(Color(0xFF1E1F22)).padding(8.dp)) {
            Text("GugleChat", color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(channels) { ch ->
                    val bg = if (current?.id == ch.id) Color(0xFF404249) else Color.Transparent
                    Surface(color = bg, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        onClick = { viewModel.selectChannel(ch) }) {
                        Text(
                            if (ch.type == "VOICE") "🔊 ${ch.name}" else "# ${ch.name}",
                            color = Color(0xFFB5BAC1), modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
        // Chat area
        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF313338))) {
            if (current != null) {
                Text("${if (current?.type == "VOICE") "🔊" else "#"} ${current?.name}",
                    color = Color.White, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp))
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), reverseLayout = true) {
                    items(messages.reversed()) { msg ->
                        MessageBubble(msg)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    OutlinedTextField(input, { input = it },
                        Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("Message") })
                    Spacer(Modifier.width(8.dp))
                    Button({
                        viewModel.sendMessage(input.trim()); input = ""
                    }, enabled = input.isNotBlank()) { Text("Send") }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: Message) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row { Text(msg.username, color = Color(0xFF80B4FF), style = MaterialTheme.typography.labelMedium) }
        Text(msg.content, color = Color(0xFFDBDEE1))
    }
}
