package dev.vanta.smartlock

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
                airWaveArmed = false
                main.post {
                    airWaveArmButton.text = "ARM AIRWAVE TO SEND"
                    sendMessage()
                }
            }
        })
    }
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var peers: Spinner
    private var socket: BluetoothSocket? = null
    private var server: BluetoothServerSocket? = null
    private var writer: PrintWriter? = null
    private val history = StringBuilder()

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(0xFF06090E.toInt())
        }
        fun label(value: String, size: Float, color: Int = 0xFF55D9FF.toInt()) =
            TextView(this).apply { text = value; textSize = size; setTextColor(color) }
        root.addView(label("V A N T A   /   M E S H", 20f))
        root.addView(label("OFFLINE ONE-TO-ONE • SECURE BLUETOOTH LINK", 11f, 0xFF8294AA.toInt()))
        status = label("Checking Bluetooth…", 12f)
        root.addView(status)
        val discover = Button(this).apply { text = "MAKE THIS PHONE DISCOVERABLE" }
        root.addView(discover)
        peers = Spinner(this)
        root.addView(peers)
        val connect = Button(this).apply { text = "CONNECT TO PAIRED DEVICE" }
        root.addView(connect)
        transcript = label("No conversation yet. Pair both phones in Android Bluetooth settings first.", 14f, 0xFFE7F2FF.toInt())
        transcript.gravity = Gravity.BOTTOM
        val scroll = ScrollView(this)
        scroll.addView(transcript)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply { hint = "Write a message…"; setTextColor(-1); setHintTextColor(0xFF8294AA.toInt()) }
        root.addView(input)
        val send = Button(this).apply { text = "SEND MESSAGE" }
        root.addView(send)
        val airWaveToggle = CheckBox(this).apply {
            text = "Enable optional AirWave gesture"
            setTextColor(0xFFE7F2FF.toInt())
            isChecked = getSharedPreferences("vanta_mesh", MODE_PRIVATE)
                .getBoolean("airwave_enabled", false)
        }
        root.addView(airWaveToggle)
        airWaveEnabled = airWaveToggle.isChecked
        airWaveArmButton = Button(this).apply {
            text = "ARM AIRWAVE TO SEND"
            isEnabled = airWaveEnabled
        }
        root.addView(airWaveArmButton)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearAcceleration == null) {
            airWaveToggle.isEnabled = false
            airWaveArmButton.isEnabled = false
            status.text = "AirWave motion sensor is unavailable on this phone."
        }
        setContentView(root)

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
            if (input.text.toString().isBlank()) {
                status.text = "Write a message first, then arm AirWave."
            } else if (linearAcceleration == null) {
                status.text = "This phone does not provide the required motion sensor."
            } else {
                airWaveArmed = true
                airWaveDetector.reset()
                airWaveArmButton.text = "AIRWAVE ARMED • WAVE TO SEND"
                status.text = "AirWave armed for this text message. Wave the phone twice to send."
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

    private fun disarmAirWave() {
        airWaveArmed = false
        if (::airWaveArmButton.isInitialized) airWaveArmButton.text = "ARM AIRWAVE TO SEND"
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        if (airWaveEnabled && airWaveArmed && linearAcceleration != null) {
            sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        disarmAirWave()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
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

    @Synchronized private fun attach(connected: BluetoothSocket) {
        try { socket?.close() } catch (_: Exception) {}
        socket = connected
        writer = PrintWriter(connected.outputStream, true)
        main.post {
            status.text = "CONNECTED • ${connected.remoteDevice.name ?: "VANTA peer"}"
            append("System: secure Bluetooth connection established.")
        }
        thread(name = "vanta-mesh-reader") {
            try {
                val reader = BufferedReader(InputStreamReader(connected.inputStream, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    val message = String(android.util.Base64.decode(line, android.util.Base64.DEFAULT), Charsets.UTF_8)
                    main.post { append("Peer: $message") }
                }
            } catch (_: Exception) { }
            main.post {
                if (socket === connected) {
                    status.text = "DISCONNECTED • reconnect to continue"
                    append("System: connection closed.")
                    socket = null
                    writer = null
                }
            }
        }
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        val out = writer
        if (out == null || out.checkError()) {
            status.text = "Not connected. Connect to a paired phone first."
            return
        }
        val encoded = android.util.Base64.encodeToString(message.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        thread(name = "vanta-mesh-send") {
            out.println(encoded)
            main.post {
                append("You: $message")
                input.setText("")
                status.text = "Message sent over the local Bluetooth link."
            }
        }
    }

    private fun append(line: String) {
        history.append(line).append('\n')
        transcript.text = history.toString()
    }

    override fun onDestroy() {
        disarmAirWave()
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
