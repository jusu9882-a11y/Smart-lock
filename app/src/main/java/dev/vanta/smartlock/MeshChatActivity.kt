package dev.vanta.smartlock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.net.wifi.p2p.WifiP2pDevice
import java.io.InputStream
import java.io.File
import java.io.OutputStream
import android.content.pm.PackageManager
import android.os.Build
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID
import kotlin.concurrent.thread

/**
 * VANTA MESH: offline direct chat with ECDH/AES-GCM application encryption,
 * encrypted local history, durable outgoing queue and delivery acknowledgements.
 */
class MeshChatActivity : Activity() {
    private val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val uuid = UUID.fromString("a8f1c2d3-4e5f-4678-9abc-1234567890ab")
    private val main = Handler(Looper.getMainLooper())
    private val writerLock = Any()
    private lateinit var security: ChatSecurity
    private lateinit var store: ChatStore
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var peers: Spinner
    private lateinit var wifiPeers: Spinner
    private lateinit var wifiTransport: WifiDirectTransport
    private lateinit var voiceButton: Button
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var discoveredWifiPeers: List<WifiP2pDevice> = emptyList()
    @Volatile private var activeConnectionId: String? = null
    @Volatile private var activeClose: (() -> Unit)? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var sessionKey: ByteArray? = null
    @Volatile private var activePeerAddress: String? = null
    private val peerPrefs by lazy { getSharedPreferences("vanta_trusted_peers", MODE_PRIVATE) }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWifiPermission(): Boolean = if (Build.VERSION.SDK_INT >= 33)
        checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    else checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestWifiPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
            else Manifest.permission.ACCESS_FINE_LOCATION
        ActivityCompat.requestPermissions(this, arrayOf(permission), 43)
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE), 41
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        security = ChatSecurity(this)
        store = ChatStore(this, security)
        VantaNotifications.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(0xFF06090E.toInt())
        }
        fun label(value: String, size: Float, color: Int = 0xFF55D9FF.toInt()) =
            TextView(this).apply { text = value; textSize = size; setTextColor(color) }
        root.addView(label("V A N T A   /   M E S H", 20f))
        root.addView(label("OFFLINE CHAT • E2E ENCRYPTED • NO ACCOUNT", 11f, 0xFF8294AA.toInt()))
        status = label("Checking Bluetooth…", 12f)
        root.addView(status)
        val help = Button(this).apply { text = "HELP / HOW TO USE VANTA" }
        root.addView(help)
        help.setOnClickListener { showHelp() }
        val discover = Button(this).apply { text = "MAKE THIS PHONE DISCOVERABLE" }
        root.addView(discover)
        peers = Spinner(this)
        root.addView(peers)
        val connect = Button(this).apply { text = "CONNECT TO PAIRED DEVICE" }
        root.addView(connect)
        root.addView(label("OFFLINE WI-FI DIRECT", 12f, 0xFF8294AA.toInt()))
        val wifiDiscover = Button(this).apply { text = "DISCOVER NEARBY WI-FI DIRECT PHONES" }
        root.addView(wifiDiscover)
        wifiPeers = Spinner(this)
        root.addView(wifiPeers)
        val wifiConnect = Button(this).apply { text = "CONNECT OVER WI-FI DIRECT" }
        root.addView(wifiConnect)
        transcript = label("Pair both phones in Android Bluetooth settings to begin.", 14f, 0xFFE7F2FF.toInt())
        transcript.gravity = Gravity.BOTTOM
        val scroll = ScrollView(this).apply { addView(transcript) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "Write a message…"; setTextColor(-1); setHintTextColor(0xFF8294AA.toInt())
        }
        root.addView(input)
        val send = Button(this).apply { text = "SEND / QUEUE MESSAGE" }
        root.addView(send)
        voiceButton = Button(this).apply { text = "RECORD VOICE NOTE (MAX 20 SEC)" }
        root.addView(voiceButton)
        val playVoice = Button(this).apply { text = "PLAY LAST VOICE NOTE" }
        root.addView(playVoice)
        setContentView(root)

        discover.setOnClickListener {
            if (!hasBluetoothPermissions()) { requestBluetoothPermissions(); return@setOnClickListener }
            val a = adapter ?: return@setOnClickListener
            if (!a.isEnabled) {
                status.text = "Turn Bluetooth on in Android settings, then return."
                startActivity(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                startActivity(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
                startServer()
            }
        }
        connect.setOnClickListener { connectSelected() }
        wifiTransport = WifiDirectTransport(this, object : WifiDirectTransport.Listener {
            override fun onPeersChanged(peers: List<WifiP2pDevice>) {
                main.post {
                    discoveredWifiPeers = peers
                    wifiPeers.adapter = ArrayAdapter(this@MeshChatActivity, android.R.layout.simple_spinner_dropdown_item,
                        peers.map { "${it.deviceName.ifBlank { "VANTA device" }} • ${it.deviceAddress}" })
                }
            }
            override fun onStatus(message: String) { main.post { status.text = message } }
            override fun onConnected(input: InputStream, output: OutputStream, close: () -> Unit, peerId: String) {
                attachStreams(input, output, close, "wifi:$peerId")
            }
        })
        wifiDiscover.setOnClickListener {
            if (!hasWifiPermission()) requestWifiPermission() else wifiTransport.startDiscovery()
        }
        wifiConnect.setOnClickListener {
            if (!hasWifiPermission()) requestWifiPermission()
            else discoveredWifiPeers.getOrNull(wifiPeers.selectedItemPosition)?.let { wifiTransport.connect(it) }
                ?: run { status.text = "Discover nearby Wi-Fi Direct phones first." }
        }
        send.setOnClickListener { sendMessage() }
        voiceButton.setOnClickListener { if (recorder == null) startVoiceRecording() else stopVoiceRecording() }
        playVoice.setOnClickListener { playLastVoiceNote() }
        if (adapter == null) status.text = "This phone does not support Bluetooth."
        else if (!hasBluetoothPermissions()) requestBluetoothPermissions()
        else refreshPeers()
    }


    private fun showHelp() {
        val guide = """
VANTA MESH — QUICK START

WHAT YOU NEED
• Two Android phones with Bluetooth.
• VANTA installed on both phones.
• Pair the phones first in Android Settings > Bluetooth.
• Keep VANTA open during a chat. Background delivery is not supported yet.

CONNECT THE PHONES
1. Turn Bluetooth on on both phones.
2. On the receiving phone, open VANTA and tap MAKE THIS PHONE DISCOVERABLE. Accept Android's visibility prompt.
3. On the other phone, choose the receiving phone from the paired-device list.
4. Tap CONNECT TO PAIRED DEVICE.
5. On first contact, compare the fingerprint shown in VANTA on both phones using a trusted separate channel. Accept only if they match.
6. Repeat the trust confirmation on the other phone if prompted. The status should say END-TO-END ENCRYPTED.

USE WI-FI DIRECT (NO ROUTER / NO INTERNET)
1. Turn Wi-Fi on on both phones. A mobile data plan or Internet connection is not needed.
2. Grant Nearby Wi-Fi devices permission (Android 13+) or Location permission (older Android) when asked. Android may also require Location services to be switched on for discovery.
3. On both phones, tap DISCOVER NEARBY WI-FI DIRECT PHONES.
4. On one phone, select the other phone from the Wi-Fi Direct list and tap CONNECT OVER WI-FI DIRECT.
5. Verify the peer fingerprint before accepting the first connection. Keep VANTA open on both phones.
6. Wi-Fi Direct is a nearby direct radio link; it does not reach another town by itself.

SEND A MESSAGE
• Type in the message field and tap SEND / QUEUE MESSAGE.
• Messages send when a secure Bluetooth or Wi-Fi Direct session is active.
• If disconnected, outgoing messages are saved encrypted on this phone and queued for the next connection.
• A delivered acknowledgement removes the queued status.

SEND A VOICE NOTE
• Tap RECORD VOICE NOTE (MAX 20 SEC), speak, then tap STOP & SEND VOICE NOTE.
• VANTA records AAC audio locally, encrypts it, and queues it for the selected peer.
• Tap PLAY LAST VOICE NOTE to listen to the latest voice note stored in this conversation.
• Keep the app open and a direct connection active to send/receive voice notes.

HISTORY & PRIVACY
• Conversation history is encrypted on this phone and remains after app restart.
• The app does not need mobile data, Wi-Fi internet, an account, or a cloud server.
• Your Bluetooth devices must be paired in Android settings.
• Never approve a new peer unless you have compared its fingerprint with the other phone.

TROUBLESHOOTING
• No devices listed: pair the phones in Android Bluetooth settings, then reopen VANTA.
• Cannot connect: enable Bluetooth on both phones, make the receiver discoverable, and retry.
• Permissions denied: allow Nearby devices / Bluetooth permissions in Android app settings.
• Peer identity changed: stop and verify the device identity. Do not accept an unexpected change.
• Messages remain queued: reconnect to the same trusted peer and keep both VANTA screens open.

CURRENT LIMITATIONS
This release supports direct Bluetooth and Wi-Fi Direct chat plus encrypted voice notes. Wi-Fi Direct works only when participating phones are physically nearby. Multi-hop relay and reliable background delivery are not available yet.
        """.trimIndent()
        val body = TextView(this).apply {
            text = guide
            textSize = 15f
            setTextColor(0xFFE7F2FF.toInt())
            setPadding(24, 12, 24, 12)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this)
            .setTitle("VANTA MESH — Help")
            .setView(scroll)
            .setPositiveButton("GOT IT", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 44) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startVoiceRecording()
            else status.text = "Microphone permission is required to record voice notes."
        } else if (requestCode == 43) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) wifiTransport.startDiscovery()
            else status.text = "Nearby Wi-Fi permission is required for Wi-Fi Direct discovery."
        } else if (requestCode == 41 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            refreshPeers()
            status.text = "Bluetooth permissions granted. Pair devices in Android settings."
        } else if (requestCode == 41) status.text = "Bluetooth permissions are required for nearby chat."
    }

    private fun pairedDevices(): List<android.bluetooth.BluetoothDevice> {
        if (!hasBluetoothPermissions()) return emptyList()
        return try { adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList() }
        catch (_: SecurityException) { emptyList() }
    }

    private fun selectedDevice() = pairedDevices().getOrNull(peers.selectedItemPosition)

    private fun refreshPeers() {
        val devices = pairedDevices()
        peers.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            devices.map { "${it.name ?: "Unknown device"} • ${it.address}" })
        if (devices.isNotEmpty() && activePeerAddress == null) activePeerAddress = devices.first().address
        status.text = if (devices.isEmpty()) "No paired devices. Pair phones in Android settings."
            else "Ready • ${devices.size} paired device(s)"
        refreshHistory()
    }

    private fun refreshHistory() {
        val peer = activePeerAddress ?: selectedDevice()?.address ?: return
        activePeerAddress = peer
        val rows = store.history(peer)
        transcript.text = if (rows.isEmpty()) "No messages yet. Messages are encrypted on this device."
            else rows.joinToString("\n") {
                "${if (it.outgoing) "You" else "Peer"}: ${if (it.text.startsWith("VOICE1:")) "[voice note]" else it.text}${if (it.outgoing && !it.delivered) "  • queued" else ""}"
            }
    }

    private fun startServer() {
        if (!hasBluetoothPermissions()) return
        thread(name = "vanta-mesh-listen") {
            try {
                server?.close()
                val listening = adapter?.listenUsingRfcommWithServiceRecord("VANTA MESH", uuid)
                    ?: throw IllegalStateException("Bluetooth adapter unavailable")
                server = listening
                main.post { status.text = "Discoverable • waiting for a paired VANTA peer…" }
                while (!isFinishing) {
                    val incoming = listening.accept() ?: break
                    attach(incoming)
                }
            } catch (e: Exception) {
                main.post { if (!isFinishing) status.text = "Listener stopped: ${e.message ?: "Bluetooth error"}" }
            }
        }
    }

    private fun connectSelected() {
        if (!hasBluetoothPermissions()) { requestBluetoothPermissions(); return }
        val device = selectedDevice()
        if (device == null) { status.text = "Pair with another phone in Android settings first."; return }
        activePeerAddress = device.address
        refreshHistory()
        status.text = "Connecting to ${device.name ?: device.address}…"
        thread(name = "vanta-mesh-connect") {
            try {
                adapter?.cancelDiscovery()
                attach(device.createRfcommSocketToServiceRecord(uuid).also { it.connect() })
            } catch (e: Exception) {
                main.post { status.text = "Connection failed: ${e.message ?: "Check pairing and retry"}" }
            }
        }
    }

    @Synchronized private fun attach(connected: BluetoothSocket) {
        attachStreams(connected.inputStream, connected.outputStream, { connected.close() }, connected.remoteDevice.address, connected)
    }

    @Synchronized private fun attachStreams(
        input: InputStream, output: OutputStream, close: () -> Unit, peer: String,
        bluetoothSocket: BluetoothSocket? = null
    ) {
        try { activeClose?.invoke() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = bluetoothSocket
        activeClose = close
        val connectionId = UUID.randomUUID().toString()
        activeConnectionId = connectionId
        writer = PrintWriter(output, true)
        sessionKey = null
        val address = peer
        activePeerAddress = address
        sendFrame("HELLO:" + android.util.Base64.encodeToString(security.publicIdentity(), android.util.Base64.NO_WRAP))
        main.post {
            status.text = "Offline link connected • negotiating encrypted session…"
            refreshHistory()
        }
        thread(name = "vanta-mesh-reader") {
            try {
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    handleFrame(bluetoothSocket, address, line)
                }
            } catch (e: Exception) {
                main.post { status.text = "Secure connection ended: ${e.message ?: "link closed"}" }
            }
            main.post {
                if (activeConnectionId == connectionId) {
                    status.text = "DISCONNECTED • queued messages are saved"
                    socket = null
                    writer = null
                    sessionKey = null
                    activeConnectionId = null
                    activeClose = null
                    try { close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun sendFrame(frame: String) {
        synchronized(writerLock) {
            val out = writer ?: return
            out.println(frame)
            if (out.checkError()) throw IllegalStateException("Bluetooth write failed")
        }
    }

    private fun handleFrame(connected: BluetoothSocket?, address: String, line: String) {
        when {
            line.startsWith("HELLO:") -> {
                val encoded = line.removePrefix("HELLO:")
                val publicKey = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                val fingerprint = security.fingerprint(publicKey)
                // Wi-Fi Direct addresses can change between groups; pin the cryptographic identity.
                val peerKey = if (address.startsWith("wifi:")) "wifi:$fingerprint" else address
                val saved = peerPrefs.getString(peerKey, null)
                if (saved != null) {
                    if (saved != encoded) {
                        main.post {
                            status.text = "SECURITY ALERT • peer identity changed. Connection rejected."
                            try { connected?.close() } catch (_: Exception) {}
                        }
                        return
                    }
                    establishSession(publicKey, peerKey, fingerprint)
                } else {
                    main.post {
                        AlertDialog.Builder(this)
                            .setTitle("Verify new VANTA peer")
                            .setMessage("Peer fingerprint:\n\n$fingerprint\n\nCompare this fingerprint with the other phone through a trusted channel before accepting.")
                            .setCancelable(false)
                            .setNegativeButton("REJECT") { _, _ -> try { connected?.close() } catch (_: Exception) {} }
                            .setPositiveButton("I VERIFIED THIS DEVICE") { _, _ ->
                                peerPrefs.edit().putString(peerKey, encoded).apply()
                                establishSession(publicKey, peerKey, fingerprint)
                            }.show()
                    }
                }
            }
            line.startsWith("MSG:") -> {
                val key = sessionKey ?: throw IllegalStateException("Encrypted session is not verified")
                val parts = line.split(":", limit = 3)
                if (parts.size != 3) throw IllegalArgumentException("Malformed message frame")
                val id = parts[1]
                val cipherText = android.util.Base64.decode(parts[2], android.util.Base64.DEFAULT)
                val plain = security.decryptMessage(key, cipherText)
                val message = String(plain, Charsets.UTF_8)
                store.addIncoming(id, activePeerAddress ?: address, message)
                VantaNotifications.showMessage(this, address)
                sendFrame("ACK:$id")
                main.post { refreshHistory() }
            }
            line.startsWith("ACK:") -> {
                store.markDelivered(line.removePrefix("ACK:"))
                main.post { refreshHistory() }
            }
            else -> throw IllegalArgumentException("Unknown VANTA protocol frame")
        }
    }

    private fun establishSession(publicKey: ByteArray, address: String, fingerprint: String) {
        sessionKey = security.deriveSessionKey(publicKey)
        activePeerAddress = address
        main.post {
            status.text = "END-TO-END ENCRYPTED • peer $fingerprint"
            refreshHistory()
            flushPending()
        }
    }

    private fun flushPending() {
        val key = sessionKey ?: return
        val peer = activePeerAddress ?: return
        thread(name = "vanta-mesh-outbox") {
            try {
                for (message in store.pending(peer)) {
                    val encrypted = security.encryptMessage(key, message.text.toByteArray(Charsets.UTF_8))
                    val payload = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
                    sendFrame("MSG:${message.id}:$payload")
                }
            } catch (e: Exception) {
                main.post { status.text = "Outbox paused • ${e.message ?: "connection issue"}" }
            }
        }
    }

    private fun startVoiceRecording() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 44)
            return
        }
        try {
            val file = File(cacheDir, "vanta_voice_${System.currentTimeMillis()}.m4a")
            val active = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setAudioEncodingBitRate(32_000)
            active.setAudioSamplingRate(22_050)
            active.setMaxDuration(20_000)
            active.setOutputFile(file.absolutePath)
            active.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) main.post { stopVoiceRecording() }
            }
            active.prepare()
            active.start()
            recorder = active
            recordingFile = file
            voiceButton.text = "STOP & SEND VOICE NOTE"
            status.text = "Recording locally • up to 20 seconds"
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            recordingFile?.delete()
            recordingFile = null
            status.text = "Could not start recording: ${e.message ?: "microphone unavailable"}"
        }
    }

    private fun stopVoiceRecording() {
        val active = recorder ?: return
        val file = recordingFile
        recorder = null
        recordingFile = null
        try {
            active.stop()
            active.release()
            voiceButton.text = "RECORD VOICE NOTE (MAX 20 SEC)"
            if (file == null || !file.exists()) {
                status.text = "Voice recording was not saved."
                return
            }
            val bytes = file.readBytes()
            file.delete()
            if (bytes.isEmpty() || bytes.size > 256 * 1024) {
                status.text = "Voice note is empty or too large. Try a shorter recording."
                return
            }
            val peer = activePeerAddress ?: selectedDevice()?.address
            if (peer == null) {
                status.text = "Connect to a peer before recording a voice note."
                return
            }
            val payload = "VOICE1:" + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            store.add(peer, payload, true)
            refreshHistory()
            status.text = "Voice note encrypted and queued • connect to send"
            if (sessionKey != null) flushPending()
        } catch (e: Exception) {
            try { active.reset(); active.release() } catch (_: Exception) {}
            file?.delete()
            voiceButton.text = "RECORD VOICE NOTE (MAX 20 SEC)"
            status.text = "Recording did not finish cleanly. Please try again."
        }
    }

    private fun playLastVoiceNote() {
        try {
            val peer = activePeerAddress ?: selectedDevice()?.address ?: return
            val encoded = store.history(peer).lastOrNull { it.text.startsWith("VOICE1:") }?.text
                ?: run { status.text = "No voice note in this conversation yet."; return }
            val bytes = android.util.Base64.decode(encoded.removePrefix("VOICE1:"), android.util.Base64.DEFAULT)
            val file = File(cacheDir, "vanta_playback.m4a")
            file.writeBytes(bytes)
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { it.release(); file.delete() }
            player.setOnErrorListener { mp, _, _ -> mp.release(); file.delete(); status.text = "Could not play this voice note."; true }
            player.prepare()
            player.start()
            status.text = "Playing encrypted voice note"
        } catch (e: Exception) {
            status.text = "Voice playback failed: ${e.message ?: "unsupported audio"}"
        }
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        val peer = activePeerAddress ?: selectedDevice()?.address
        if (peer == null) {
            status.text = "Pair and select a phone before sending or queueing messages."
            return
        }
        store.add(peer, message, true)
        input.setText("")
        refreshHistory()
        if (sessionKey == null) status.text = "Saved securely • will send when the peer reconnects"
        else flushPending()
    }

    override fun onDestroy() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        recordingFile?.delete()
        try { activeClose?.invoke() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        wifiTransport.close()
        store.close()
        super.onDestroy()
    }
}
