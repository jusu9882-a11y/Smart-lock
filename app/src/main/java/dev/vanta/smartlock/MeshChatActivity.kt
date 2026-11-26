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
import android.media.MediaRecorder
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
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
    private var recorder: MediaRecorder? = null
    private var pendingAudioFile: File? = null
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
        val audioRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        recordAudioButton = Button(this).apply { text = "RECORD VOICE" }
        val chooseAudio = Button(this).apply { text = "ATTACH AUDIO" }
        audioRow.addView(recordAudioButton, LinearLayout.LayoutParams(0, -2, 1f))
        audioRow.addView(chooseAudio, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(audioRow)
        audioStatus = label("No voice note selected", 11f, 0xFF9EB2C8.toInt())
        root.addView(audioStatus)
        previewAudioButton = Button(this).apply { text = "PLAY VOICE PREVIEW"; isEnabled = false }
        root.addView(previewAudioButton)
        discardAudioButton = Button(this).apply { text = "DISCARD VOICE"; isEnabled = false }
        root.addView(discardAudioButton)
        val send = Button(this).apply { text = "SEND MESSAGE / VOICE" }
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
        recordAudioButton.setOnClickListener {
            if (recorder == null) startVoiceRecording() else stopVoiceRecording(previewAudioButton, discardAudioButton)
        }
        chooseAudio.setOnClickListener {
            startActivityForResult(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "audio/*"
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
                    if (line.startsWith("AUDIO|")) {
                        val parts = line.split('|', limit = 3)
                        if (parts.size == 3) {
                            val safeName = parts[1].replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
                            val bytes = android.util.Base64.decode(parts[2], android.util.Base64.DEFAULT)
                            val dir = File(filesDir, "mesh_received").apply { mkdirs() }
                            val received = File(dir, "${System.currentTimeMillis()}_$safeName")
                            received.writeBytes(bytes)
                            main.post { append("Peer sent audio: ${received.name} (${bytes.size / 1024} KB)") }
                        }
                    } else {
                        val message = String(android.util.Base64.decode(line, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        main.post { append("Peer: $message") }
                    }
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
        if (message.isEmpty() && pendingAudioFile != null) { sendPendingAudio(); return }
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


    private fun startVoiceRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 74)
            return
        }
        val file = File(cacheDir, "vanta_voice_${System.currentTimeMillis()}.m4a")
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

    private fun sendPendingAudio() {
        val file = pendingAudioFile ?: return
        val out = writer
        if (out == null || out.checkError()) {
            status.text = "Not connected. Connect to a paired phone first."
            return
        }
        if (file.length() !in 1..audioLimitBytes) {
            status.text = "Audio must be between 1 byte and 15 MB."
            return
        }
        val encoded = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
        thread(name = "vanta-mesh-audio-send") {
            out.println("AUDIO|voice_${System.currentTimeMillis()}.m4a|$encoded")
            main.post {
                append("You sent a voice note.")
                file.delete()
                pendingAudioFile = null
                audioStatus.text = "Voice note sent"
                status.text = "Voice note transferred over Bluetooth."
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 73 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val target = File(cacheDir, "vanta_audio_${System.currentTimeMillis()}.audio")
        try {
            require(contentResolver.getType(uri).orEmpty().startsWith("audio/")) { "Choose an audio file." }
            contentResolver.openInputStream(uri)?.use { source ->
                FileOutputStream(target).use { sink ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= audioLimitBytes) { "Audio file exceeds 15 MB." }
                        sink.write(buffer, 0, count)
                    }
                }
            } ?: error("Unable to open selected audio.")
            require(target.length() > 0) { "Selected file is empty." }
            pendingAudioFile?.delete()
            pendingAudioFile = target
            audioStatus.text = "Audio attached • ${target.length() / 1024} KB"
            previewAudioButton.isEnabled = true
            discardAudioButton.isEnabled = true
        } catch (error: Exception) {
            target.delete()
            audioStatus.text = "Attachment failed: ${error.message ?: "file error"}"
        }
    }

    private fun append(line: String) {
        history.append(line).append('\n')
        transcript.text = history.toString()
    }

    override fun onDestroy() {
        disarmAirWave()
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        recordingFile?.delete()
        recordingFile = null
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
