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
                var messages by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(VaultStore.load(this)) }
                Column(Modifier.fillMaxSize().background(VaultBlack).padding(20.dp)) {
                    Text("VOID MESSAGE VAULT", color = VaultCyan, fontSize = 19.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("WhatsApp notification previews · encrypted on this device", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("ENABLE NOTIFICATION ACCESS")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { VaultStore.clear(this@MessageVaultActivity); messages = emptyList() }) {
                        Text("CLEAR VAULT")
                    }
                    Spacer(Modifier.height(12.dp))
                    if (messages.isEmpty()) {
                        Text("No captured notifications yet.", color = Color.LightGray)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(messages, key = { it.id }) { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111923))) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(item.sender, color = VaultCyan)
                                        Text(item.kind, color = Color.Gray, fontSize = 10.sp)
                                        Text(item.content, color = Color.White)
                                        Text(SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
                                            .format(Date(item.timestamp)), color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Only notification text exposed by Android is saved. Actual WhatsApp audio files are not accessible through notification access.", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}
