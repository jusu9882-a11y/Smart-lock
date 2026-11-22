package dev.vanta.smartlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Build
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VoidBlack = Color(0xFF06090E)
private val Cyan = Color(0xFF55D9FF)
private val Muted = Color(0xFF8294AA)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = VoidBlack) {
                    VantaHome(
                        authenticated = unlocked,
                        onAuthenticate = { showAuthenticationPrompt() },
                        onLockDevice = { lockDeviceNow() },
                        onReLock = { unlocked = false },
                        deviceAdminActive = isDeviceAdminActive()
                    )
                }
            }
        }
    }

    private fun showAuthenticationPrompt() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            return
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock VANTA")
            .setSubtitle("Authenticate using biometrics or your device credential")
            .setAllowedAuthenticators(authenticators)
            .build()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    unlocked = false
                }
                override fun onAuthenticationFailed() {
                    unlocked = false
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    private var unlocked by mutableStateOf(false)

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, VantaDeviceAdminReceiver::class.java))
    }

    private fun lockDeviceNow() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, VantaDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            startActivity(android.content.Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enable VANTA's explicit Lock Device button to lock using Android's system lock.")
            })
        }
    }
}

@Composable
private fun VantaHome(authenticated: Boolean, onAuthenticate: () -> Unit, onLockDevice: () -> Unit, onReLock: () -> Unit, deviceAdminActive: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var time by remember { mutableStateOf("--:--") }
    var date by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("SYSTEM AUTHENTICATION READY") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date = SimpleDateFormat("EEE · dd MMM", Locale.getDefault()).format(now)
                .uppercase(Locale.getDefault())
            delay(1000)
        }
    }
    val animation = rememberInfiniteTransition(label = "voidCore")
    val pulse by animation.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    val spin by animation.animateFloat(0f, 360f, infiniteRepeatable(tween(9000)), label = "spin")

    Column(
        Modifier.fillMaxSize().background(VoidBlack).padding(horizontal = 26.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("V A N T A  /  V O I D", color = Muted, fontSize = 12.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(24.dp))
            Text(time, color = Color.White, fontSize = 56.sp, letterSpacing = 1.sp)
            Text(date, color = Muted, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("ANDROID AUTHENTICATION", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Cyan.copy(alpha = .10f * pulse), size.minDimension * .46f)
                    drawCircle(Cyan.copy(alpha = .5f), size.minDimension * .43f, style = Stroke(1.dp.toPx()))
                    rotate(spin) {
                        drawArc(Cyan, 20f, 78f, false, style = Stroke(3.dp.toPx()))
                        drawArc(Cyan.copy(alpha = .5f), 205f, 42f, false, style = Stroke(1.5.dp.toPx()))
                    }
                    drawCircle(Cyan.copy(alpha = .25f), size.minDimension * .34f, style = Stroke(1.dp.toPx()))
                }
                Box(Modifier.size(142.dp).border(2.dp, Cyan.copy(alpha = pulse), CircleShape)
                    .background(Cyan.copy(alpha = .04f), CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VANTA", color = Cyan, fontSize = 22.sp, letterSpacing = 3.sp)
                        Text("AUTH GATE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(status, color = Cyan, fontSize = 12.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(18.dp))
            Button(onClick = if (authenticated) onReLock else onAuthenticate) { Text(if (authenticated) "LOCK VANTA" else "AUTHENTICATE") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onLockDevice) { Text(if (deviceAdminActive) "LOCK DEVICE NOW" else "ENABLE SYSTEM LOCK") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { context.startActivity(android.content.Intent(context, MessageVaultActivity::class.java)) }) { Text("VOID MESSAGE VAULT") }
            Spacer(Modifier.height(12.dp))
            Text(if (authenticated) "VANTA session unlocked on this screen." else "Authentication opens Android's secure prompt.", color = Muted, fontSize = 10.sp)
            Text("Device lock uses Android Device Administration.", color = Muted, fontSize = 10.sp)
        }
        Text("LOCAL ONLY  •  NO ADS  •  NO TELEMETRY", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}
