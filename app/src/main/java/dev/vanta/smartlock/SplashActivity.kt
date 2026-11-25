package dev.vanta.smartlock

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Branded lightweight launch screen; no extra splash dependency required. */
class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val launch = Runnable {
        if (!isFinishing) {
            startActivity(Intent(this, MeshChatActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(6, 9, 14)
        window.navigationBarColor = android.graphics.Color.rgb(6, 9, 14)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF06090E.toInt())
        }
        val logo = TextView(this).apply {
            text = "V A N T A"
            textSize = 34f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.22f
            gravity = Gravity.CENTER
            setTextColor(0xFF55D9FF.toInt())
        }
        val mesh = TextView(this).apply {
            text = "M E S H"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFE7F2FF.toInt())
        }
        val powered = TextView(this).apply {
            text = "Powered by JUSU tech team"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF8294AA.toInt())
        }
        root.addView(logo)
        root.addView(mesh)
        val gap = android.view.View(this)
        root.addView(gap, LinearLayout.LayoutParams(1, 28))
        root.addView(powered)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(launch)
        handler.postDelayed(launch, 1800)
    }

    override fun onPause() {
        handler.removeCallbacks(launch)
        super.onPause()
    }
}
