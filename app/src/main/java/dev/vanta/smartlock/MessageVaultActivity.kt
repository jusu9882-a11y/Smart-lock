package dev.vanta.smartlock

import android.media.MediaPlayer
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
import java.io.File
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
                var vaultUnlocked by remember { mutableStateOf(false) }
                var authStatus by remember { mutableStateOf("Authenticate to open your private vault.") }
                val authenticateVault = {
                    val allowed = if (android.os.Build.VERSION.SDK_INT >= 30) {
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    } else {
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                    }
                    if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
                        authStatus = "System authentication is unavailable on this device."
                    } else {
                        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock VANTA Message Vault")
                            .setSubtitle("Use your system biometric or device credential")
                            .setAllowedAuthenticators(allowed)
                        val prompt = BiometricPrompt(
                            this,
                            ContextCompat.getMainExecutor(this),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    // A late callback must never unlock the vault after the
                                    // activity has already moved to the background.
                                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                        vaultUnlocked = true
                                        authStatus = "Vault unlocked."
                                    } else {
                                        vaultUnlocked = false
                                        authStatus = "Vault relocked. Authenticate to continue."
                                    }
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    authStatus = errString.toString()
                                }
                                override fun onAuthenticationFailed() {
                                    authStatus = "Authentication failed. Try again."
                                }
                            }
                        )
                        prompt.authenticate(infoBuilder.build())
                    }
                }
                LaunchedEffect(Unit) { authenticateVault() }
                // Keep the lifecycle observer in sync with Compose state. A plain
                // captured Boolean would remain at its initial value (false).
                val latestVaultUnlocked by rememberUpdatedState(vaultUnlocked)
                val latestAuthStatus = rememberUpdatedState<(String) -> Unit> { authStatus = it }
                DisposableEffect(Unit) {
                    var backgrounded = false
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                if (latestVaultUnlocked) {
                                    vaultUnlocked = false
                                    latestAuthStatus.value("Vault relocked. Authenticate to continue.")
                                }
                                backgrounded = true
                            }
                            Lifecycle.Event.ON_START -> {
                                if (backgrounded) {
                                    backgrounded = false
                                    authenticateVault()
                                }
                            }
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                if (!vaultUnlocked) {
                    Column(Modifier.fillMaxSize().background(VaultBlack).padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("VANTA / VOID", color = VaultCyan, fontSize = 20.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("MESSAGE VAULT LOCKED", color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(authStatus, color = Color.LightGray, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { authenticateVault() }) { Text("AUTHENTICATE") }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { finish() }) { Text("CLOSE VAULT") }
                    }
                } else {
                var messages by remember { mutableStateOf(VaultStore.load(this)) }
                var voiceNotes by remember { mutableStateOf(VoiceNoteStore.list(this)) }
                var importStatus by remember { mutableStateOf("Voice notes are imported only when you choose the files.") }
                var player by remember { mutableStateOf<MediaPlayer?>(null) }
                var playingName by remember { mutableStateOf<String?>(null) }
                var playbackTemp by remember { mutableStateOf<File?>(null) }

                val audioPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        val result = VoiceNoteStore.importAudio(this, uri)
                        importStatus = result.fold(
                            onSuccess = { "Imported: ${it.name}" },
                            onFailure = { "Import failed: ${it.message ?: "unsupported audio file"}" }
                        )
                        voiceNotes = VoiceNoteStore.list(this)
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        player?.release()
                        player = null
                        playbackTemp?.delete()
                        playbackTemp = null
                    }
                }

                Column(Modifier.fillMaxSize().background(VaultBlack).padding(20.dp)) {
                    Text("VANTA / WHATSAPP MODE", color = VaultCyan, fontSize = 19.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Message previews and your selected voice-note files", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("ENABLE NOTIFICATION ACCESS")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                        Text("IMPORT WHATSAPP AUDIO")
                    }
                    Text(importStatus, color = Color.Gray, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        VaultStore.clear(this@MessageVaultActivity)
                        messages = emptyList()
                    }) { Text("CLEAR MESSAGE ARCHIVE") }

                    Spacer(Modifier.height(18.dp))
                    Text("SAVED VOICE NOTES", color = VaultCyan, fontSize = 14.sp)
                    if (voiceNotes.isEmpty()) {
                        Text("No audio files imported. Choose an audio file from your device to add it here.", color = Color.LightGray, fontSize = 12.sp)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(voiceNotes, key = { it.name }) { note ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111923))) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(note.name, color = Color.White, fontSize = 12.sp)
                                            Text(note.sizeLabel + if (note.encrypted) " · ENCRYPTED" else " · LEGACY FILE", color = Color.Gray, fontSize = 10.sp)
                                        }
                                        TextButton(onClick = {
                                            try {
                                                player?.release()
                                                playbackTemp?.delete()
                                                val playableFile = VoiceNoteStore.prepareForPlayback(this@MessageVaultActivity, note)
                                                playbackTemp = if (note.encrypted) playableFile else null
                                                player = MediaPlayer().apply {
                                                    setDataSource(playableFile.absolutePath)
                                                    setOnCompletionListener {
                                                        it.release()
                                                        player = null
                                                        playbackTemp?.delete()
                                                        playbackTemp = null
                                                        playingName = null
                                                    }
                                                    prepare()
                                                    start()
                                                }
                                                playingName = note.name
                                                importStatus = "Playing: ${note.name}"
                                            } catch (e: Exception) {
                                                player?.release()
                                                player = null
                                                playbackTemp?.delete()
                                                playbackTemp = null
                                                playingName = null
                                                importStatus = "Could not play audio: ${e.message ?: "unsupported format"}"
                                            }
                                        }) { Text(if (playingName == note.name) "PLAYING" else "PLAY") }
                                        TextButton(onClick = {
                                            if (playingName == note.name) {
                                                player?.release()
                                                player = null
                                                playbackTemp?.delete()
                                                playbackTemp = null
                                                playingName = null
                                            }
                                            VoiceNoteStore.delete(note)
                                            voiceNotes = VoiceNoteStore.list(this@MessageVaultActivity)
                                        }) { Text("DELETE") }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("SAVED WHATSAPP PREVIEWS", color = VaultCyan, fontSize = 14.sp)
                    if (messages.isEmpty()) {
                        Text("No previews captured yet. Enable notification access and receive a WhatsApp message with previews enabled.", color = Color.LightGray, fontSize = 12.sp)
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(messages, key = { it.id }) { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111923))) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(item.sender.ifBlank { "WhatsApp" }, color = VaultCyan)
                                        if (item.kind.isNotBlank()) Text(item.kind, color = Color.Gray, fontSize = 10.sp)
                                        if (item.content.isNotBlank()) Text(item.content, color = Color.White)
                                        Text(SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(item.timestamp)),
                                            color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Audio import uses Android's file picker: you choose each file and VANTA copies it into private app storage. It does not extract audio from WhatsApp or bypass app security. Deleted audio can only be imported if you still have an accessible copy or backup.", color = Color.Gray, fontSize = 10.sp)
                } 
                }
            }
        }
    }
}
