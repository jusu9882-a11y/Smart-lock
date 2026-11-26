package dev.vanta.smartlock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import java.net.Socket
import java.io.Closeable
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.MediaRecorder
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.OutputStreamWriter
import java.util.UUID
import kotlin.concurrent.thread

/**
 * VANTA MESH v1: direct, internet-free, one-to-one chat over Android secure
 * Bluetooth RFCOMM. Android's bonded-device link encryption is used; this is
 * not yet an application-layer end-to-end protocol or a multi-hop mesh.
 */
class MeshChatActivity : Activity(), SensorEventListener {
    private val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val uuid: UUID = UUID.fromString("a8f1c2d3-4e5f-4678-9abc-1234567890ab")
    private val main = Handler(Looper.getMainLooper())
    private lateinit var sensorManager: SensorManager
    private var linearAcceleration: Sensor? = null
    private var airWaveEnabled = false
    private var airWaveArmed = false
    private lateinit var airWaveArmButton: Button
    private val airWaveDetector by lazy {
        AirWaveMotionDetector(onWave = {
            if (airWaveArmed) {
                main.post {
                    disarmAirWave()
                    sendMessage()
                }
            }
        })
    }
    private lateinit var status: TextView
    private lateinit var transcript: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var peers: Spinner
    private lateinit var wifiPeers: Spinner
    private var wifiPeerDevices: List<WifiP2pDevice> = emptyList()
    private lateinit var wifiTransport: WifiDirectTransport
    @Volatile private var chatVisible = false
    private val notificationChannelId = "vanta_mesh_messages"
    private var server: BluetoothServerSocket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var activeSocket: Closeable? = null
    private val sendLock = Any()
    private var sending = false
    private val history = StringBuilder()
    private var recorder: MediaRecorder? = null
    private var pendingAudioFile: File? = null
    private var pendingAttachmentMime: String = "audio/*"
    private var pendingAttachmentName: String = "attachment.bin"
    private var recordingFile: File? = null
    private lateinit var audioStatus: TextView
    private lateinit var recordAudioButton: Button
    private lateinit var previewAudioButton: Button
    private lateinit var discardAudioButton: Button
    private val audioLimitBytes = 15L * 1024L * 1024L

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE),
            41
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun rounded(fill: Int, stroke: Int = 0xFF26384A.toInt(), radius: Float = 18f) =
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(radius.toInt()).toFloat()
                setStroke(dp(1), stroke)
            }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF08111C.toInt(), 0xFF06090E.toInt(), 0xFF05070B.toInt())
            )
        }
        fun label(value: String, size: Float, color: Int = 0xFF55D9FF.toInt()) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
        fun styleAction(button: Button, primary: Boolean = false) {
            button.isAllCaps = false
            button.textSize = 12f
            button.minHeight = dp(48)
            button.setPadding(dp(14), dp(10), dp(14), dp(10))
            button.setTextColor(if (primary) 0xFF061018.toInt() else 0xFFDCEBFA.toInt())
            button.background = rounded(
                if (primary) 0xFF55D9FF.toInt() else 0xFF142333.toInt(),
                if (primary) 0xFF55D9FF.toInt() else 0xFF29445C.toInt(),
                14f
            )
            button.stateListAnimator = null
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xFF101D2A.toInt(), 0xFF25445A.toInt(), 18f)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("VANTA MESH", 20f))
        brand.addView(label("PRIVATE • OFFLINE • DEVICE TO DEVICE", 9f, 0xFF8294AA.toInt()))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label("● LOCAL", 10f, 0xFF65E6B4.toInt()))
        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        val subhead = label("YOUR PRIVATE CONVERSATION", 10f, 0xFF8294AA.toInt())
        subhead.letterSpacing = 0.12f
        subhead.setPadding(dp(4), dp(18), dp(4), dp(8))
        root.addView(subhead)
        status = label("Checking Bluetooth…", 11f, 0xFFB9D0E4.toInt())
        status.setPadding(dp(14), dp(12), dp(14), dp(12))
        status.background = rounded(0xFF101D2A.toInt(), 0xFF29445C.toInt(), 14f)
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        val helpButton = Button(this).apply { text = "HOW TO USE VANTA MESH" }
        styleAction(helpButton)
        root.addView(helpButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        helpButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("VANTA MESH • Quick guide")
                .setMessage(
                    """
                    1. Turn on Bluetooth or Wi-Fi on both phones.

                    2. Bluetooth: pair both phones in Android Settings. On one phone tap Make discoverable; on the other select the paired phone and tap Connect.

                    3. Wi-Fi Direct: grant Nearby Wi-Fi permission on both phones. On one phone tap Host Wi-Fi. On the other tap Find Wi-Fi Peers, select the host, then tap Connect Wi-Fi Peer.

                    4. Wait until the status says CONNECTED. Then send text, record a voice note, or attach a file.

                    5. Voice: tap Record Voice, then Stop & Preview. File: tap Attach File. Use Send to transfer.

                    6. AirWave is optional. Enable it, prepare a draft or attachment, then arm it. A deliberate phone wave sends the selected item once. It does not recognise a fist or an open hand.

                    7. Chat history is encrypted on this phone. Use Clear Chat History to erase it. Incoming notifications work while the chat session remains active in the background.

                    VANTA works without mobile data or Internet access. Wi-Fi Direct is local range only; it cannot reach another town without network infrastructure.
                    """.trimIndent()
                )
                .setPositiveButton("GOT IT", null)
                .show()
        }
        val discover = Button(this).apply { text = "Make discoverable" }
        styleAction(discover)
        root.addView(discover, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        peers = Spinner(this)
        peers.setPadding(dp(8), dp(8), dp(8), dp(8))
        root.addView(peers, LinearLayout.LayoutParams(-1, -2))
        val connect = Button(this).apply { text = "Connect to paired device" }
        styleAction(connect, true)
        root.addView(connect, LinearLayout.LayoutParams(-1, -2))
        val wifiTitle = label("WI-FI DIRECT • LOCAL ONLY", 10f, 0xFF55D9FF.toInt())
        wifiTitle.setPadding(dp(4), dp(14), dp(4), dp(6))
        root.addView(wifiTitle)
        wifiPeers = Spinner(this)
        wifiPeers.setPadding(dp(8), dp(8), dp(8), dp(8))
        root.addView(wifiPeers, LinearLayout.LayoutParams(-1, -2))
        val wifiActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val wifiDiscover = Button(this).apply { text = "FIND WI-FI PEERS" }
        val wifiHost = Button(this).apply { text = "HOST WI-FI" }
        styleAction(wifiDiscover)
        styleAction(wifiHost)
        wifiActions.addView(wifiDiscover, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        wifiActions.addView(wifiHost, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        root.addView(wifiActions)
        val wifiConnect = Button(this).apply { text = "CONNECT WI-FI PEER" }
        styleAction(wifiConnect, true)
        root.addView(wifiConnect, LinearLayout.LayoutParams(-1, -2))
        transcript = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        val welcome = label("No messages yet. Pair both phones in Android Bluetooth settings, then connect to start chatting.", 13f, 0xFF9EB2C8.toInt())
        welcome.setPadding(dp(12), dp(14), dp(12), dp(14))
        transcript.addView(welcome)
        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(4))
            background = rounded(0xFF0C1722.toInt(), 0xFF1D3448.toInt(), 18f)
            addView(transcript)
        }
        root.addView(chatScroll, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(8); bottomMargin = dp(12) })
        val clearHistory = Button(this).apply { text = "CLEAR CHAT HISTORY" }
        styleAction(clearHistory)
        root.addView(clearHistory, LinearLayout.LayoutParams(-1, -2))
        clearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear chat history?")
                .setMessage("This permanently clears the encrypted message history stored on this phone.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR") { _, _ ->
                    MeshChatStore.clear(this)
                    transcript.removeAllViews()
                    append("System: chat history cleared.", persist = false)
                }
                .show()
        }
        input = EditText(this).apply {
            hint = "Write a message…"
            setTextColor(0xFFE7F2FF.toInt())
            setHintTextColor(0xFF8294AA.toInt())
            textSize = 15f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(0xFF101D2A.toInt(), 0xFF29445C.toInt(), 16f)
            minHeight = dp(54)
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2))
        val audioRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        recordAudioButton = Button(this).apply { text = "RECORD VOICE" }
        val chooseAudio = Button(this).apply { text = "ATTACH FILE" }
        styleAction(recordAudioButton)
        styleAction(chooseAudio)
        audioRow.addView(recordAudioButton, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        audioRow.addView(chooseAudio, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        root.addView(audioRow)
        audioStatus = label("No voice note selected", 11f, 0xFF9EB2C8.toInt())
        audioStatus.setPadding(dp(4), dp(8), dp(4), dp(4))
        root.addView(audioStatus)
        previewAudioButton = Button(this).apply { text = "Preview attachment"; isEnabled = false }
        styleAction(previewAudioButton)
        root.addView(previewAudioButton, LinearLayout.LayoutParams(-1, -2))
        discardAudioButton = Button(this).apply { text = "Discard attachment"; isEnabled = false }
        styleAction(discardAudioButton)
        root.addView(discardAudioButton, LinearLayout.LayoutParams(-1, -2))
        val send = Button(this).apply { text = "Send message / file" }
        styleAction(send, true)
        root.addView(send, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val airWaveToggle = CheckBox(this).apply {
            text = "Enable optional AirWave gesture"
            setTextColor(0xFFE7F2FF.toInt())
            isChecked = getSharedPreferences("vanta_mesh", MODE_PRIVATE)
                .getBoolean("airwave_enabled", false)
        }
        airWaveToggle.setPadding(dp(4), dp(8), dp(4), dp(4))
        root.addView(airWaveToggle)
        airWaveEnabled = airWaveToggle.isChecked
        airWaveArmButton = Button(this).apply {
            text = "ARM AIRWAVE TO SEND"
            isEnabled = airWaveEnabled
        }
        styleAction(airWaveArmButton)
        root.addView(airWaveArmButton, LinearLayout.LayoutParams(-1, -2))
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearAcceleration == null) {
            airWaveToggle.isEnabled = false
            airWaveArmButton.isEnabled = false
            status.text = "AirWave motion sensor is unavailable on this phone."
        }
        val page = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(Color.TRANSPARENT)
            addView(root)
        }
        setContentView(page)
        createChatNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 43)
        }
        val restoredMessages = MeshChatStore.load(this)
        if (restoredMessages.isNotEmpty()) {
            transcript.removeAllViews()
            restoredMessages.forEach { item -> append("${item.sender}: ${item.content}", persist = false) }
        }

        wifiTransport = WifiDirectTransport(
            this,
            onPeers = { found ->
                wifiPeerDevices = found
                val labels = found.map { "${it.deviceName.ifBlank { "VANTA device" }} • ${it.deviceAddress}" }
                wifiPeers.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
                if (found.isNotEmpty()) status.text = "Found ${found.size} Wi-Fi Direct peer(s). Select one to connect."
                else status.text = "No Wi-Fi Direct peers found yet. Keep both phones' Wi-Fi Direct discovery active."
            },
            onConnected = { connected, peerName -> attachTcp(connected, peerName) },
            onStatus = { status.text = it }
        )
        wifiTransport.register()
        fun requestWifiPermission(): Boolean {
            if (wifiTransport.hasPermission()) return true
            val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
                else Manifest.permission.ACCESS_FINE_LOCATION
            ActivityCompat.requestPermissions(this, arrayOf(permission), 42)
            status.text = "Grant nearby Wi-Fi permission to use Wi-Fi Direct."
            return false
        }
        wifiDiscover.setOnClickListener { if (requestWifiPermission()) wifiTransport.discover() }
        wifiHost.setOnClickListener { if (requestWifiPermission()) wifiTransport.createGroup() }
        wifiConnect.setOnClickListener {
            if (!requestWifiPermission()) return@setOnClickListener
            val selected = wifiPeerDevices.getOrNull(wifiPeers.selectedItemPosition)
            if (selected == null) status.text = "Find a Wi-Fi Direct peer first."
            else wifiTransport.connect(selected)
        }

        discover.setOnClickListener {
            if (!hasBluetoothPermissions()) { requestBluetoothPermissions(); return@setOnClickListener }
            val a = adapter ?: return@setOnClickListener
            if (!a.isEnabled) {
                status.text = "Turn Bluetooth on in Android settings, then return."
                startActivity(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                startActivity(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
                startServer()
            }
        }
        connect.setOnClickListener { connectSelected() }
        send.setOnClickListener { disarmAirWave(); sendMessage() }
        recordAudioButton.setOnClickListener {
            if (recorder == null) startVoiceRecording() else stopVoiceRecording(previewAudioButton, discardAudioButton)
        }
        chooseAudio.setOnClickListener {
            startActivityForResult(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, 73)
        }
        previewAudioButton.setOnClickListener { playPendingAudio() }
        discardAudioButton.setOnClickListener {
            pendingAudioFile?.delete()
            pendingAudioFile = null
            audioStatus.text = "Voice note discarded"
            previewAudioButton.isEnabled = false
            discardAudioButton.isEnabled = false
        }
        airWaveToggle.setOnCheckedChangeListener { _, enabled ->
            airWaveEnabled = enabled
            airWaveArmed = false
            airWaveArmButton.isEnabled = enabled && linearAcceleration != null
            airWaveArmButton.text = "ARM AIRWAVE TO SEND"
            getSharedPreferences("vanta_mesh", MODE_PRIVATE).edit()
                .putBoolean("airwave_enabled", enabled).apply()
            if (!enabled) sensorManager.unregisterListener(this)
        }
        airWaveArmButton.setOnClickListener {
            if (!airWaveEnabled) return@setOnClickListener
            if (input.text.toString().isBlank() && pendingAudioFile == null) {
                status.text = "Write a message or attach a voice note first, then arm AirWave."
            } else if (linearAcceleration == null) {
                status.text = "This phone does not provide the required motion sensor."
            } else {
                airWaveArmed = true
                airWaveDetector.reset()
                airWaveArmButton.text = "AIRWAVE ARMED • WAVE TO SEND"
                status.text = "AirWave armed for the current draft or attachment. Wave the phone twice to send."
                sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        if (adapter == null) status.text = "This phone does not support Bluetooth."
        else if (!hasBluetoothPermissions()) requestBluetoothPermissions()
        else refreshPeers()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!airWaveEnabled || !airWaveArmed || event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        airWaveDetector.onLinearAcceleration(event.values[0], event.values[1], event.values[2], event.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun createChatNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(notificationChannelId, "VANTA MESH messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Incoming messages and files while a VANTA MESH session is active." }
            )
        }
    }

    private fun notifyIncoming(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openChat = android.content.Intent(this, MeshChatActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 91, openChat, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body.take(140))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        getSystemService(NotificationManager::class.java).notify(91, notification)
    }

    private fun disarmAirWave() {
        airWaveArmed = false
        if (::airWaveArmButton.isInitialized) airWaveArmButton.text = "ARM AIRWAVE TO SEND"
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        chatVisible = true
        if (airWaveEnabled && airWaveArmed && linearAcceleration != null) {
            sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        chatVisible = false
        disarmAirWave()
        if (recorder != null) stopVoiceRecording(previewAudioButton, discardAudioButton)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 74) {
            status.text = if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) "Microphone permission granted. Tap RECORD VOICE again." else "Microphone permission is required to record voice notes."
        } else if (requestCode == 43) {
            status.text = if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) "Chat notifications enabled." else "Notifications are disabled. You can enable them in Android settings."
        } else if (requestCode == 42 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            wifiTransport.discover()
            status.text = "Nearby Wi-Fi permission granted."
        } else if (requestCode == 41 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            refreshPeers()
            status.text = "Bluetooth permissions granted. Pair devices in Android settings."
        } else status.text = "Bluetooth permissions are required for nearby chat."
    }

    private fun refreshPeers() {
        if (!hasBluetoothPermissions()) return
        try {
            val devices = adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList()
            val names = devices.map { "${it.name ?: "Unknown device"} • ${it.address}" }
            peers.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            status.text = if (devices.isEmpty()) "No paired devices. Pair the phones in Android Bluetooth settings." else "Ready • ${devices.size} paired device(s)"
        } catch (_: SecurityException) { status.text = "Bluetooth permission unavailable." }
    }

    private fun startServer() {
        if (!hasBluetoothPermissions()) return
        thread(name = "vanta-mesh-listen") {
            try {
                server?.close()
                server = adapter?.listenUsingRfcommWithServiceRecord("VANTA MESH", uuid)
                main.post { status.text = "Listening for a paired VANTA phone…" }
                val incoming = server?.accept() ?: return@thread
                attach(incoming)
            } catch (e: Exception) {
                main.post { status.text = "Listener stopped: ${e.message ?: "Bluetooth error"}" }
            }
        }
    }

    private fun connectSelected() {
        if (!hasBluetoothPermissions()) { requestBluetoothPermissions(); return }
        val device = try {
            val list = adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList()
            list.getOrNull(peers.selectedItemPosition)
        } catch (_: SecurityException) { null }
        if (device == null) { status.text = "Pair with another phone in Android settings first."; return }
        status.text = "Connecting to ${device.name ?: device.address}…"
        thread(name = "vanta-mesh-connect") {
            try {
                adapter?.cancelDiscovery()
                attach(device.createRfcommSocketToServiceRecord(uuid).also { it.connect() })
            } catch (e: Exception) {
                main.post { status.text = "Connection failed: ${e.message ?: "Check pairing and try again"}" }
            }
        }
    }

    private fun attach(connected: BluetoothSocket) {
        attachStreams(connected.inputStream, connected.outputStream, connected,
            connected.remoteDevice.name ?: "Bluetooth peer")
    }

    private fun attachTcp(connected: Socket, peerName: String) {
        attachStreams(connected.getInputStream(), connected.getOutputStream(), connected, peerName)
    }

    @Synchronized private fun attachStreams(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        connected: Closeable,
        peerName: String
    ) {
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = connected
        writer = PrintWriter(OutputStreamWriter(outputStream, Charsets.UTF_8), true)
        main.post {
            status.text = "CONNECTED • $peerName"
            append("System: secure local connection established.")
        }
        thread(name = "vanta-mesh-reader") {
            try {
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("FILE|")) {
                        val parts = line.split('|', limit = 4)
                        if (parts.size == 4 && parts[3].length <= 21_000_000) {
                            val mime = parts[1].take(120)
                            val safeName = parts[2].replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
                            val bytes = android.util.Base64.decode(parts[3], android.util.Base64.DEFAULT)
                            if (bytes.size <= audioLimitBytes.toInt()) {
                                val dir = File(filesDir, "mesh_received").apply { mkdirs() }
                                val received = File(dir, "${System.currentTimeMillis()}_$safeName")
                                received.writeBytes(bytes)
                                main.post {
                                    append("Peer sent file: ${received.name} • $mime • ${bytes.size / 1024} KB")
                                    if (!chatVisible) notifyIncoming("File received", received.name)
                                }
                            }
                        }
                    } else if (line.startsWith("AUDIO|")) {
                        val parts = line.split('|', limit = 3)
                        if (parts.size == 3 && parts[2].length <= 21_000_000) {
                            val bytes = android.util.Base64.decode(parts[2], android.util.Base64.DEFAULT)
                            if (bytes.size in 1..audioLimitBytes.toInt()) {
                                val dir = File(filesDir, "mesh_received").apply { mkdirs() }
                                val safeName = parts[1].replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
                                val received = File(dir, "${System.currentTimeMillis()}_$safeName")
                                received.writeBytes(bytes)
                                main.post {
                                    append("Peer sent voice note: ${received.name} • ${bytes.size / 1024} KB")
                                    if (!chatVisible) notifyIncoming("Voice note received", received.name)
                                }
                            }
                        }
                    } else {
                        val message = String(android.util.Base64.decode(line, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        main.post {
                            append("Peer: $message")
                            if (!chatVisible) notifyIncoming("New message", message)
                        }
                    }
                }
            } catch (error: Exception) {
                main.post { if (activeSocket === connected) status.text = "Connection interrupted: ${error.message ?: "transfer error"}" }
            }
            main.post {
                if (activeSocket === connected) {
                    status.text = "DISCONNECTED • reconnect to continue"
                    append("System: connection closed.")
                    activeSocket = null
                    writer = null
                    try { connected.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        val attachment = pendingAudioFile
        if (message.isEmpty() && attachment == null) return
        val out = writer
        if (out == null || out.checkError()) {
            status.text = "Not connected. Connect to a nearby phone first."
            return
        }
        if (attachment != null && attachment.length() !in 1..audioLimitBytes) {
            status.text = "Attachment must be between 1 byte and 15 MB."
            return
        }
        val mime = pendingAttachmentMime
        val fileName = pendingAttachmentName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
        status.text = "Sending over the active local connection…"
        thread(name = "vanta-mesh-send") {
            try {
                val encodedText = if (message.isNotEmpty())
                    android.util.Base64.encodeToString(message.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                else null
                val encodedFile = attachment?.let {
                    android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP)
                }
                val sent = synchronized(sendLock) {
                    if (writer !== out || out.checkError()) {
                        false
                    } else {
                        if (encodedText != null) out.println(encodedText)
                        if (encodedFile != null) out.println("FILE|$mime|$fileName|$encodedFile")
                        !out.checkError()
                    }
                }
                main.post {
                    if (!sent) {
                        status.text = "Send failed. Check the connection and try again; your draft is kept."
                    } else {
                        if (message.isNotEmpty()) append("You: $message")
                        input.setText("")
                        if (attachment != null && pendingAudioFile == attachment) {
                            append("You sent file: $fileName")
                            attachment.delete()
                            pendingAudioFile = null
                            audioStatus.text = "File sent"
                            previewAudioButton.isEnabled = false
                            discardAudioButton.isEnabled = false
                        }
                        status.text = "Sent over the active local connection."
                    }
                }
            } catch (error: Exception) {
                main.post { status.text = "Send failed: ${error.message ?: "local transfer error"}. Your draft is kept." }
            }
        }
    }

    private fun startVoiceRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 74)
            return
        }
        val stamp = System.currentTimeMillis()
        val file = File(cacheDir, "vanta_voice_${stamp}.m4a")
        pendingAttachmentMime = "audio/mp4"
        pendingAttachmentName = "voice_${stamp}.m4a"
        try {
            val active = MediaRecorder()
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setAudioEncodingBitRate(96000)
            active.setAudioSamplingRate(44100)
            active.setOutputFile(file.absolutePath)
            active.prepare()
            active.start()
            recorder = active
            recordingFile = file
            recordAudioButton.text = "STOP & PREVIEW"
            audioStatus.text = "Recording… press STOP & PREVIEW when finished"
        } catch (error: Exception) {
            file.delete()
            recorder?.release()
            recorder = null
            audioStatus.text = "Recording failed: ${error.message ?: "microphone error"}"
        }
    }

    private fun stopVoiceRecording(preview: Button, discard: Button) {
        val active = recorder ?: return
        try {
            active.stop()
            val file = recordingFile ?: error("Recording file missing.")
            require(file.length() in 1..audioLimitBytes) { "Voice note is empty or exceeds 15 MB." }
            pendingAudioFile?.delete()
            pendingAudioFile = file
            audioStatus.text = "Voice note ready • ${file.length() / 1024} KB"
            previewAudioButton.isEnabled = true
            discardAudioButton.isEnabled = true
        } catch (error: Exception) {
            recordingFile?.delete()
            recordingFile = null
            audioStatus.text = "Recording discarded: ${error.message ?: "recording failed"}"
        } finally {
            active.release()
            recorder = null
            recordingFile = null
            recordAudioButton.text = "RECORD VOICE"
        }
    }

    private fun playPendingAudio() {
        val file = pendingAudioFile ?: return
        if (!pendingAttachmentMime.startsWith("audio/")) { audioStatus.text = "Preview is only available for audio files."; return }
        try {
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ -> mp.release(); true }
            player.prepare()
            player.start()
            audioStatus.text = "Playing voice preview…"
        } catch (error: Exception) {
            audioStatus.text = "Preview failed: ${error.message ?: "audio error"}"
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 73 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val target = File(cacheDir, "vanta_audio_${System.currentTimeMillis()}.audio")
        try {
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            contentResolver.openInputStream(uri)?.use { source ->
                FileOutputStream(target).use { sink ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= audioLimitBytes) { "File exceeds the 15 MB transfer limit." }
                        sink.write(buffer, 0, count)
                    }
                }
            } ?: error("Unable to open selected file.")
            require(target.length() > 0) { "Selected file is empty." }
            pendingAudioFile?.delete()
            pendingAudioFile = target
            pendingAttachmentMime = mime
            pendingAttachmentName = queryDisplayName(uri) ?: "attachment_${System.currentTimeMillis()}"
            audioStatus.text = "File attached: ${pendingAttachmentName} • ${target.length() / 1024} KB"
            previewAudioButton.isEnabled = true
            discardAudioButton.isEnabled = true
        } catch (error: Exception) {
            target.delete()
            audioStatus.text = "Attachment failed: ${error.message ?: "file error"}"
        }
    }

    private fun append(line: String, persist: Boolean = true) {
        history.append(line).append('\n')
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val isMine = line.startsWith("You:")
        val isPeer = line.startsWith("Peer:")
        val isSystem = line.startsWith("System:")
        if (persist && (isMine || isPeer)) {
            val sender = if (isMine) "You" else "Peer"
            val content = line.removePrefix("You: ").removePrefix("Peer: ")
            MeshChatStore.add(this, sender, content)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = when {
                isMine -> Gravity.END
                isSystem -> Gravity.CENTER_HORIZONTAL
                else -> Gravity.START
            }
            setPadding(0, dp(4), 0, dp(4))
        }
        val bubble = TextView(this).apply {
            text = line.removePrefix("You: ").removePrefix("Peer: ").removePrefix("System: ")
            textSize = if (isSystem) 10f else 14f
            setTextColor(when {
                isMine -> 0xFF061018.toInt()
                isSystem -> 0xFF8294AA.toInt()
                else -> 0xFFE7F2FF.toInt()
            })
            setPadding(dp(13), dp(10), dp(13), dp(10))
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
            background = GradientDrawable().apply {
                setColor(when {
                    isMine -> 0xFF55D9FF.toInt()
                    isSystem -> 0xFF172431.toInt()
                    else -> 0xFF1A2B3B.toInt()
                })
                cornerRadius = dp(16).toFloat()
                if (!isSystem) setStroke(dp(1), if (isMine) 0xFF55D9FF.toInt() else 0xFF29445C.toInt())
            }
        }
        row.addView(bubble, LinearLayout.LayoutParams(-2, -2))
        transcript.addView(row, LinearLayout.LayoutParams(-1, -2))
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        disarmAirWave()
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        recordingFile?.delete()
        recordingFile = null
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = null
        try { server?.close() } catch (_: Exception) {}
        if (::wifiTransport.isInitialized) wifiTransport.close()
        super.onDestroy()
    }
}
