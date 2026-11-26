package dev.vanta.smartlock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Minimal VANTA MESH splash. The chat is the only user-facing app destination. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF08111C.toInt(), 0xFF05070B.toInt())
            )
        }
        val brand = TextView(this).apply {
            text = "VANTA MESH"
            textSize = 28f
            letterSpacing = 0.18f
            setTextColor(0xFF55D9FF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "PRIVATE  •  OFFLINE  •  DEVICE TO DEVICE"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(0xFF8294AA.toInt())
            gravity = Gravity.CENTER
        }
        val powered = TextView(this).apply {
            text = "Powered by JUSU tech team"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(brand)
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(powered, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(42) })
        setContentView(root)
        handler.postDelayed({
            if (!isFinishing) {
                startActivity(Intent(this, MeshChatActivity::class.java))
                finish()
            }
        }, 1100L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
