package dev.vanta.smartlock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VaultBlack = Color(0xFF06090E)
private val VaultCyan = Color(0xFF55D9FF)

class MessageVaultActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var messages by remember { mutableStateOf(MessageVaultStore.read(this)) }
                Column(Modifier.fillMaxSize().background(VaultBlack).padding(20.dp)) {
                    Text("VANTA / WHATSAPP MODE", color = VaultCyan, fontSize = 19.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Private archive of WhatsApp notification previews", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("ENABLE NOTIFICATION ACCESS")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { MessageVaultStore.clear(this@MessageVaultActivity); messages = emptyList() }) {
                        Text("CLEAR WHATSAPP ARCHIVE")
                    }
                    Spacer(Modifier.height(12.dp))
                    if (messages.isEmpty()) {
                        Text("No WhatsApp previews captured yet. Enable notification access, then receive a WhatsApp message with previews enabled.", color = Color.LightGray)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(messages, key = { "${it.receivedAt}-${it.packageName}-${it.title}-${it.body}" }) { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111923))) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(item.title.ifBlank { item.app }, color = VaultCyan)
                                        if (item.title.isNotBlank()) Text(item.app, color = Color.Gray, fontSize = 10.sp)
                                        if (item.body.isNotBlank()) Text(item.body, color = Color.White)
                                        Text(SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(item.receivedAt)),
                                            color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("LIMITS: only notification text Android exposes after access is enabled can be saved. This does not read WhatsApp chats or recover earlier deleted messages. Voice-note audio files are not captured by notification access.",
                        color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}
