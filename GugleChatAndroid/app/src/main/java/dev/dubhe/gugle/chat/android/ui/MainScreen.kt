package dev.dubhe.gugle.chat.android.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.dubhe.gugle.chat.android.data.Channel
import dev.dubhe.gugle.chat.android.data.Message
import dev.dubhe.gugle.chat.android.viewmodel.ChatViewModel
import io.noties.markwon.Markwon
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val DiscordBg = Color(0xFF313338)
val DiscordSidebar = Color(0xFF1E1F22)
val DiscordAccent = Color(0xFF5865F2)
val DiscordText = Color(0xFFDBDEE1)
val DiscordMuted = Color(0xFF949BA4)
val DiscordDivider = Color(0xFF2B2D31)

@Composable
fun MainScreen(viewModel: ChatViewModel, onLogout: () -> Unit) {
    val channels by viewModel.channels.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val current by viewModel.currentChannel.collectAsState()
    var showChannels by remember { mutableStateOf(true) }
    var prevChannel by remember { mutableStateOf<Channel?>(null) }
    var input by remember { mutableStateOf("") }

    // Auto-switch to chat ONLY when channel was just selected (not on back)
    if (current != null && current != prevChannel) {
        prevChannel = current
        showChannels = false
    }
    if (current == null && !showChannels) showChannels = true

    // Back button returns to channel list instead of exiting
    BackHandler(enabled = !showChannels) { showChannels = true }

    if (showChannels || current == null) {
        ChannelListScreen(channels, viewModel::selectChannel, onLogout)
    } else {
        ChatScreen(current!!, messages, input, { input = it }, {
            if (input.isNotBlank()) {
                viewModel.sendMessage(input.trim()); input = ""
            }
        }, { showChannels = true })
    }
}

@Composable
fun ChannelListScreen(channels: List<Channel>, onSelect: (Channel) -> Unit, onLogout: () -> Unit) {
    Column(Modifier
        .fillMaxSize()
        .background(DiscordSidebar)) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("GugleChat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            TextButton(onClick = onLogout) {
                Text(
                    "Logout",
                    color = DiscordMuted,
                    fontSize = 13.sp
                )
            }
        }
        LazyColumn {
            items(channels) { ch ->
                Surface(
                    selected = false,
                    onClick = { onSelect(ch) },
                    modifier = Modifier.fillMaxWidth(),
                    color = DiscordSidebar
                ) {
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (ch.type == "VOICE") "🔊" else "#",
                            color = DiscordMuted,
                            fontSize = 18.sp,
                            modifier = Modifier.width(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.name,
                                color = DiscordText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (ch.type == "VOICE") "Voice" else "${ch.memberCount} members",
                                color = DiscordMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    channel: Channel, messages: List<Message>,
    input: String, onInput: (String) -> Unit,
    onSend: () -> Unit, onBack: () -> Unit
) {
    Column(Modifier
        .fillMaxSize()
        .background(DiscordBg)) {
        // Top bar with back
        Surface(Modifier.fillMaxWidth(), color = DiscordBg) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("☰", color = DiscordText, fontSize = 20.sp) }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${if (channel.type == "VOICE") "🔊 " else "# "}${channel.name}",
                    color = DiscordText, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
        }
        HorizontalDivider(color = DiscordDivider)

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg -> MessageBubble(msg) }
        }

        HorizontalDivider(color = DiscordDivider)
        Row(Modifier
            .fillMaxWidth()
            .padding(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                input, onInput, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Message #${channel.name}", color = DiscordMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF383A40),
                    unfocusedContainerColor = Color(0xFF383A40),
                    focusedTextColor = DiscordText, unfocusedTextColor = DiscordText,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend, enabled = input.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = DiscordAccent)
            ) {
                Text("➤", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun MessageBubble(msg: Message) {
    val ctx = LocalContext.current
    val avatarColor = Color(msg.username.hashCode() or 0xFF000000.toInt())
    val markwon = remember {
        Markwon.builder(ctx)
            .usePlugin(CoilImagesPlugin.create(ctx))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                Modifier.size(36.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = avatarColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        msg.username.first().uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        msg.username,
                        color = Color(0xFF80B4FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        try {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        } catch (_: Exception) {
                            ""
                        },
                        color = DiscordMuted, fontSize = 11.sp
                    )
                }
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            movementMethod = LinkMovementMethod.getInstance()
                            setTextColor(0xFFDBDEE1.toInt())
                            textSize = 15f
                            setLineSpacing(4f, 1f)
                        }
                    },
                    update = { tv -> markwon.setMarkdown(tv, msg.content) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
