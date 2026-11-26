package dev.vanta.smartlock

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * VANTA MESH home and section navigation.
 * Transport and message operations remain in MeshChatActivity; this screen
 * keeps the entry point clean and separates discovery, AirWave and settings.
 */
class MainActivity : Activity() {
    private val ink = 0xFF07111C.toInt()
    private val panel = 0xFF101E2C.toInt()
    private val muted = 0xFF91A6BB.toInt()
    private val cyan = 0xFF55D9FF.toInt()
    private lateinit var pageHost: LinearLayout
    private val pages = mutableMapOf<String, View>()
    private val navButtons = mutableMapOf<String, Button>()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun surface(color: Int, stroke: Int = 0xFF29445C.toInt(), radius: Int = 18) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun text(value: String, size: Float, color: Int = 0xFFE7F2FF.toInt(), bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

    private fun action(value: String, primary: Boolean = false, onClick: () -> Unit) =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 13f
            minHeight = dp(50)
            setTextColor(if (primary) ink else 0xFFDCEBFA.toInt())
            background = surface(if (primary) cyan else 0xFF172A3B.toInt(),
                if (primary) cyan else 0xFF31516B.toInt(), 15)
            stateListAnimator = null
            setOnClickListener { onClick() }
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = surface(panel)
    }

    private fun addCard(parent: LinearLayout, title: String, description: String) {
        val item = card()
        item.addView(text(title, 16f, 0xFFEAF6FF.toInt(), true))
        item.addView(text(description, 12f, muted).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        parent.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF081421.toInt(), 0xFF05090F.toInt()))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(18))
        }
        header.addView(text("VANTA MESH", 23f, cyan, true))
        header.addView(text("PRIVATE  •  OFFLINE  •  DEVICE TO DEVICE", 9f, muted).apply {
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        root.addView(header)

        pageHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(7), dp(5), dp(7))
            background = surface(0xFF101D2A.toInt(), 0xFF233B50.toInt(), 20)
        }
        listOf("Chats" to "chats", "Connect" to "connect", "AirWave" to "airwave", "Settings" to "settings")
            .forEach { (label, key) ->
                val button = Button(this).apply {
                    text = label
                    isAllCaps = false
                    textSize = 10f
                    minHeight = dp(44)
                    minWidth = 0
                    setPadding(dp(2), dp(4), dp(2), dp(4))
                    stateListAnimator = null
                    setOnClickListener { showPage(key) }
                }
                navButtons[key] = button
                nav.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
            }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        buildChatsPage()
        buildConnectPage()
        buildAirWavePage()
        buildSettingsPage()
        showPage("chats")
        setContentView(root)
    }

    private fun page(key: String): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(column)
        }
        pages[key] = scroll
        return column
    }

    private fun showPage(key: String) {
        pageHost.removeAllViews()
        pages[key]?.let { pageHost.addView(it, LinearLayout.LayoutParams(-1, -1)) }
        navButtons.forEach { (name, button) ->
            val selected = name == key
            button.setTextColor(if (selected) cyan else muted)
            button.background = if (selected) surface(0xFF1B3448.toInt(), 0xFF315D79.toInt(), 13)
                else surface(0xFF101D2A.toInt(), 0xFF101D2A.toInt(), 13)
        }
    }

    private fun buildChatsPage() {
        val content = page("chats")
        content.addView(text("Messages", 25f, 0xFFEAF6FF.toInt(), true))
        content.addView(text("Your conversations", 12f, muted).apply {
            setPadding(0, dp(4), 0, dp(18))
        })
        val conversation = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = TextView(this).apply {
            text = "V"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(ink)
            background = surface(cyan, cyan, 40)
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(46), dp(46)))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        details.addView(text("VANTA MESH", 15f, 0xFFEAF6FF.toInt(), true))
        details.addView(text("Offline device-to-device chat", 11f, muted).apply {
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        conversation.addView(row)
        conversation.addView(action("OPEN MESSAGES", true) {
            startActivity(Intent(this, MeshChatActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        content.addView(conversation, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        addCard(content, "Designed for nearby devices",
            "Send text, voice notes and files directly between phones. No account or mobile data is needed.")
        addCard(content, "Your privacy stays local",
            "VANTA MESH uses Bluetooth or Wi-Fi Direct for a direct local connection. Wi-Fi Direct does not provide town-to-town range.")
    }

    private fun buildConnectPage() {
        val content = page("connect")
        content.addView(text("Connections", 25f, 0xFFEAF6FF.toInt(), true))
        content.addView(text("Choose how nearby phones connect.", 12f, muted).apply {
            setPadding(0, dp(4), 0, dp(18))
        })
        addCard(content, "Bluetooth",
            "Pair both phones in Android Bluetooth settings. VANTA can then discover and connect to the paired device.")
        addCard(content, "Wi-Fi Direct",
            "Create a local peer-to-peer Wi-Fi group or discover a nearby host. This is still offline and does not use an Internet connection.")
        content.addView(action("OPEN CONNECTION MANAGER", true) {
            startActivity(Intent(this, MeshChatActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildAirWavePage() {
        val content = page("airwave")
        content.addView(text("AirWave", 25f, 0xFFEAF6FF.toInt(), true))
        content.addView(text("Optional hands-free controls", 12f, muted).apply {
            setPadding(0, dp(4), 0, dp(18))
        })
        val settings = getSharedPreferences("vanta_mesh", MODE_PRIVATE)
        val toggleCard = card()
        val toggle = CheckBox(this).apply {
            text = "Enable AirWave"
            textSize = 15f
            setTextColor(0xFFEAF6FF.toInt())
            buttonTintList = android.content.res.ColorStateList.valueOf(cyan)
            isChecked = settings.getBoolean("airwave_enabled", false)
            setOnCheckedChangeListener { _, enabled ->
                settings.edit().putBoolean("airwave_enabled", enabled).apply()
            }
        }
        toggleCard.addView(toggle)
        toggleCard.addView(text("AirWave is off by default. When enabled, you must prepare a message or attachment and arm the send action before a gesture can transmit it.", 12f, muted).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(toggleCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        addCard(content, "Phone-motion wave",
            "Uses the phone's motion sensor to trigger a prepared send after you explicitly arm it.")
        addCard(content, "Camera hand gestures",
            "The optional camera mode supports making a fist to start voice recording, opening the hand to stop, and waving to send an armed item. Camera permission is requested only when you enable this mode.")
        content.addView(action("CONFIGURE AIRWAVE IN CHAT") {
            startActivity(Intent(this, MeshChatActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildSettingsPage() {
        val content = page("settings")
        content.addView(text("Settings", 25f, 0xFFEAF6FF.toInt(), true))
        content.addView(text("VANTA MESH preferences and help", 12f, muted).apply {
            setPadding(0, dp(4), 0, dp(18))
        })
        addCard(content, "Offline by design",
            "Messages and attachments move directly over Bluetooth or Wi-Fi Direct. The app does not require mobile data or Internet service for device-to-device transfers.")
        addCard(content, "Compatibility",
            "VANTA MESH targets Android 9 and newer. Bluetooth, Wi-Fi Direct, microphone and camera availability depend on the phone and Android permissions.")
        addCard(content, "Powered by JUSU tech team",
            "VANTA MESH • Private offline messaging")
        content.addView(action("OPEN HELP & CHAT") {
            startActivity(Intent(this, MeshChatActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2))
    }
}
