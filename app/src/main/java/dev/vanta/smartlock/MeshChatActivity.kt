package dev.vanta.smartlock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
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
        val discover = Button(this).apply { text = "MAKE THIS PHONE DISCOVERABLE" }
        root.addView(discover)
        peers = Spinner(this)
        root.addView(peers)
        val connect = Button(this).apply { text = "CONNECT TO PAIRED DEVICE" }
        root.addView(connect)
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
        send.setOnClickListener { sendMessage() }
        if (adapter == null) status.text = "This phone does not support Bluetooth."
        else if (!hasBluetoothPermissions()) requestBluetoothPermissions()
        else refreshPeers()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            refreshPeers()
            status.text = "Bluetooth permissions granted. Pair devices in Android settings."
        } else status.text = "Bluetooth permissions are required for nearby chat."
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
                "${if (it.outgoing) "You" else "Peer"}: ${it.text}${if (it.outgoing && !it.delivered) "  • queued" else ""}"
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
                while (!isFinishing && !listening.isClosed) {
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
        try { socket?.close() } catch (_: Exception) {}
        socket = connected
        writer = PrintWriter(connected.outputStream, true)
        sessionKey = null
        val address = connected.remoteDevice.address
        activePeerAddress = address
        sendFrame("HELLO:" + android.util.Base64.encodeToString(security.publicIdentity(), android.util.Base64.NO_WRAP))
        main.post {
            status.text = "Bluetooth linked • negotiating encrypted session…"
            refreshHistory()
        }
        thread(name = "vanta-mesh-reader") {
            try {
                val reader = BufferedReader(InputStreamReader(connected.inputStream, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    handleFrame(connected, address, line)
                }
            } catch (e: Exception) {
                main.post { status.text = "Secure connection ended: ${e.message ?: "link closed"}" }
            }
            main.post {
                if (socket === connected) {
                    status.text = "DISCONNECTED • queued messages are saved"
                    socket = null
                    writer = null
                    sessionKey = null
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

    private fun handleFrame(connected: BluetoothSocket, address: String, line: String) {
        when {
            line.startsWith("HELLO:") -> {
                val encoded = line.removePrefix("HELLO:")
                val publicKey = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                val fingerprint = security.fingerprint(publicKey)
                val saved = peerPrefs.getString(address, null)
                if (saved != null) {
                    if (saved != encoded) {
                        main.post {
                            status.text = "SECURITY ALERT • peer identity changed. Connection rejected."
                            try { connected.close() } catch (_: Exception) {}
                        }
                        return
                    }
                    establishSession(publicKey, address, fingerprint)
                } else {
                    main.post {
                        AlertDialog.Builder(this)
                            .setTitle("Verify new VANTA peer")
                            .setMessage("Peer fingerprint:\n\n$fingerprint\n\nCompare this fingerprint with the other phone through a trusted channel before accepting.")
                            .setCancelable(false)
                            .setNegativeButton("REJECT") { _, _ -> try { connected.close() } catch (_: Exception) {} }
                            .setPositiveButton("I VERIFIED THIS DEVICE") { _, _ ->
                                peerPrefs.edit().putString(address, encoded).apply()
                                establishSession(publicKey, address, fingerprint)
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
                store.addIncoming(id, address, message)
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
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        store.close()
        super.onDestroy()
    }
}
