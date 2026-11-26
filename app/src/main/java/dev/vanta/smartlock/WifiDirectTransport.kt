package dev.vanta.smartlock

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Local-only Wi-Fi Direct transport. It never resolves a public host or contacts
 * an Internet service; sockets are opened only to the negotiated P2P group owner.
 */
internal class WifiDirectTransport(
    private val activity: Activity,
    private val onPeers: (List<WifiP2pDevice>) -> Unit,
    private val onConnected: (Socket, String) -> Unit,
    private val onStatus: (String) -> Unit
) : Closeable {
    private val manager = activity.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = manager?.initialize(activity, Looper.getMainLooper(), null)
    private val main = Handler(Looper.getMainLooper())
    private var registered = false
    @Volatile private var socketStarted = false
    @Volatile private var closed = false
    @Volatile private var serverSocket: ServerSocket? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        report("Wi-Fi Direct is disabled. Enable Wi-Fi in Android settings.")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
            }
        }
    }

    fun register() {
        if (registered) return
        if (manager == null || channel == null) { report("This Android device does not support Wi-Fi Direct."); return }
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun hasPermission(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        activity.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    } else {
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun discover() {
        if (!hasPermission()) {
            report("Grant Nearby Wi-Fi permission before discovering Wi-Fi Direct peers.")
            return
        }
        val mgr = manager ?: return report("This Android device does not support Wi-Fi Direct.")
        val ch = channel ?: return report("Wi-Fi Direct is unavailable on this device.")
        try {
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = report("Searching for nearby Wi-Fi Direct devices…")
                override fun onFailure(reason: Int) = report("Wi-Fi Direct discovery failed (code $reason).")
            })
        } catch (error: SecurityException) {
            report("Wi-Fi Direct permission is missing.")
        }
    }

    fun createGroup() {
        if (!hasPermission()) {
            report("Grant Nearby Wi-Fi permission before hosting a Wi-Fi Direct chat.")
            return
        }
        socketStarted = false
        val mgr = manager ?: return report("This Android device does not support Wi-Fi Direct.")
        val ch = channel ?: return report("Wi-Fi Direct is unavailable on this device.")
        try {
            mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = report("Creating a local Wi-Fi Direct group…")
                override fun onFailure(reason: Int) = report("Could not host Wi-Fi Direct (code $reason).")
            })
        } catch (error: SecurityException) {
            report("Wi-Fi Direct permission is missing.")
        }
    }

    fun connect(device: WifiP2pDevice) {
        if (!hasPermission()) {
            report("Grant Nearby Wi-Fi permission before connecting.")
            return
        }
        socketStarted = false
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        val mgr = manager ?: return report("This Android device does not support Wi-Fi Direct.")
        val ch = channel ?: return report("Wi-Fi Direct is unavailable on this device.")
        try {
            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = report("Negotiating Wi-Fi Direct connection with ${device.deviceName}…")
                override fun onFailure(reason: Int) = report("Wi-Fi Direct connection failed (code $reason).")
            })
        } catch (error: SecurityException) {
            report("Wi-Fi Direct permission is missing.")
        }
    }

    private fun requestPeers() {
        if (!hasPermission()) return
        val mgr = manager ?: return report("This Android device does not support Wi-Fi Direct.")
        val ch = channel ?: return report("Wi-Fi Direct is unavailable on this device.")
        try {
            mgr.requestPeers(ch) { list ->
                val peers = list.deviceList.toList()
                main.post { onPeers(peers) }
            }
        } catch (_: SecurityException) {
            report("Wi-Fi Direct permission is missing.")
        }
    }

    private fun requestConnectionInfo() {
        if (!hasPermission()) return
        val mgr = manager ?: return report("This Android device does not support Wi-Fi Direct.")
        val ch = channel ?: return report("Wi-Fi Direct is unavailable on this device.")
        try {
            mgr.requestConnectionInfo(ch) { info -> handleConnection(info) }
        } catch (_: SecurityException) {
            report("Wi-Fi Direct permission is missing.")
        }
    }

    @Synchronized
    private fun handleConnection(info: WifiP2pInfo) {
        if (closed || !info.groupFormed || socketStarted) return
        socketStarted = true
        if (info.isGroupOwner) {
            thread(name = "vanta-wifi-direct-server") {
                try {
                    val server = ServerSocket(8988)
                    serverSocket = server
                    report("Wi-Fi Direct group ready. Waiting for the peer…")
                    val accepted = server.accept()
                    server.close()
                    serverSocket = null
                    if (!closed) main.post { onConnected(accepted, "Wi-Fi Direct peer") }
                    else accepted.close()
                } catch (error: Exception) {
                    socketStarted = false
                    report("Wi-Fi Direct host socket failed: ${error.message ?: "connection error"}")
                }
            }
        } else {
            val address = info.groupOwnerAddress ?: run {
                socketStarted = false
                report("Wi-Fi Direct group owner address is unavailable.")
                return
            }
            thread(name = "vanta-wifi-direct-client") {
                try {
                    val client = Socket()
                    client.connect(InetSocketAddress(address, 8988), 20_000)
                    if (!closed) main.post { onConnected(client, "Wi-Fi Direct peer") }
                    else client.close()
                } catch (error: Exception) {
                    socketStarted = false
                    report("Wi-Fi Direct socket failed: ${error.message ?: "connection error"}")
                }
            }
        }
    }

    private fun report(message: String) {
        main.post { if (!closed) onStatus(message) }
    }

    override fun close() {
        closed = true
        if (registered) {
            try { activity.unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        if (hasPermission()) {
            val mgr = manager
            val ch = channel
            if (mgr != null && ch != null) try {
                mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = Unit
                    override fun onFailure(reason: Int) = Unit
                })
            } catch (_: SecurityException) {}
        }
    }
}
