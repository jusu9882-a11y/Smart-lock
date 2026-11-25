package dev.vanta.smartlock

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Local-only Wi-Fi Direct transport. It creates a peer-to-peer Wi-Fi group
 * and carries VANTA's existing encrypted line protocol over a local socket.
 * It never resolves hostnames, opens Internet endpoints, or uses a server.
 */
class WifiDirectTransport(
    context: Context,
    private val listener: Listener
) : Closeable {
    interface Listener {
        fun onPeersChanged(peers: List<WifiP2pDevice>)
        fun onStatus(message: String)
        fun onConnected(input: InputStream, output: OutputStream, close: () -> Unit, peerId: String)
    }

    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(app, Looper.getMainLooper(), null)
    private val closed = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    listener.onStatus(if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                        "Wi-Fi Direct is on" else "Turn Wi-Fi on to use offline Wi-Fi Direct")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val network = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (network?.isConnected == true) requestConnectionInfo()
                    else {
                        connecting.set(false)
                        listener.onStatus("Wi-Fi Direct peer disconnected")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (closed.get()) return
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") app.registerReceiver(receiver, filter)
            registered = true
        }
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { listener.onStatus("Searching for nearby offline Wi-Fi Direct devices…") }
            override fun onFailure(reason: Int) { listener.onStatus("Wi-Fi Direct discovery failed (code $reason)") }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        manager.requestPeers(channel) { list -> listener.onPeersChanged(list.deviceList.toList()) }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        if (closed.get()) return
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = android.net.wifi.WpsInfo.PBC
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { listener.onStatus("Negotiating offline Wi-Fi Direct link…") }
            override fun onFailure(reason: Int) { listener.onStatus("Wi-Fi Direct connection failed (code $reason)") }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        if (!connecting.compareAndSet(false, true)) return
        manager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
            if (!info.groupFormed) {
                connecting.set(false)
                return@requestConnectionInfo
            }
            if (info.isGroupOwner) acceptPeer() else connectToOwner(info)
        }
    }

    private fun acceptPeer() {
        thread(name = "vanta-wifi-direct-server") {
            try {
                server?.close()
                val socketServer = ServerSocket(8988)
                server = socketServer
                listener.onStatus("Wi-Fi Direct group ready • waiting for peer")
                val socket = socketServer.accept()
                client = socket
                listener.onConnected(socket.getInputStream(), socket.getOutputStream(),
                    { closeSockets() }, socket.inetAddress.hostAddress ?: "wifi-direct-peer")
            } catch (e: Exception) {
                connecting.set(false)
                if (!closed.get()) listener.onStatus("Wi-Fi Direct listener stopped: ${e.message ?: "link error"}")
            }
        }
    }

    private fun connectToOwner(info: WifiP2pInfo) {
        thread(name = "vanta-wifi-direct-client") {
            try {
                val socket = Socket()
                client = socket
                socket.connect(InetSocketAddress(info.groupOwnerAddress, 8988), 15_000)
                listener.onConnected(socket.getInputStream(), socket.getOutputStream(),
                    { closeSockets() }, info.groupOwnerAddress.hostAddress ?: "wifi-direct-peer")
            } catch (e: Exception) {
                connecting.set(false)
                if (!closed.get()) listener.onStatus("Wi-Fi Direct socket failed: ${e.message ?: "link error"}")
            }
        }
    }

    private fun closeSockets() {
        try { client?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        client = null
        server = null
        connecting.set(false)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeSockets()
        if (registered) {
            try { app.unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
    }
}
