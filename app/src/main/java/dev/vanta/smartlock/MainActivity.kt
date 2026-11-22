package dev.vanta.smartlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VoidBlack = Color(0xFF06090E)
private val Cyan = Color(0xFF55D9FF)
private val Muted = Color(0xFF8294AA)
private val TargetSequence = listOf(4, 2, 7) // demo interaction only

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = VoidBlack) {
                    VoidHome()
                }
            }
        }
    }
}

@Composable
private fun VoidHome() {
    var time by remember { mutableStateOf("--:--") }
    var date by remember { mutableStateOf("") }
    var digit by remember { mutableIntStateOf(0) }
    var entered by remember { mutableStateOf(emptyList<Int>()) }
    var message by remember { mutableStateOf("CORE STANDBY") }
    var demoUnlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date = SimpleDateFormat("EEE · dd MMM", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
            delay(1000)
        }
    }

    val transition = rememberInfiniteTransition(label = "corePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000)),
        label = "rotation"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(VoidBlack).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("V A N T A   /   V O I D", color = Muted, fontSize = 12.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(30.dp))
            Text(time, color = Color.White, fontSize = 58.sp, letterSpacing = 1.sp)
            Text(date, color = Muted, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("PERSONAL SECURITY INTERFACE", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(250.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Cyan.copy(alpha = .10f * pulse), radius = size.minDimension * .47f)
                    drawCircle(Cyan.copy(alpha = .55f), radius = size.minDimension * .43f, style = Stroke(width = 1.dp.toPx()))
                    rotate(rotation) {
                        drawArc(Cyan, startAngle = 18f, sweepAngle = 78f, useCenter = false, style = Stroke(width = 3.dp.toPx()))
                        drawArc(Cyan.copy(alpha = .45f), startAngle = 205f, sweepAngle = 44f, useCenter = false, style = Stroke(width = 1.5.dp.toPx()))
                    }
                    drawCircle(Cyan.copy(alpha = .22f), radius = size.minDimension * .34f, style = Stroke(width = 1.dp.toPx()))
                }
                Box(
                    modifier = Modifier.size(144.dp).border(2.dp, Cyan.copy(alpha = pulse), CircleShape)
                        .background(Cyan.copy(alpha = .035f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (demoUnlocked) "OPEN" else "VOID", color = Cyan, fontSize = 23.sp, letterSpacing = 3.sp)
                        Text("CORE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(message, color = Cyan, fontSize = 13.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                if (demoUnlocked) "DEMO SEQUENCE ACCEPTED" else "ROTARY CIPHER · DEMO ONLY",
                color = Muted, fontSize = 10.sp
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                (0..2).forEach { i ->
                    Box(
                        modifier = Modifier.size(42.dp).border(1.dp, Cyan.copy(alpha = .65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(entered.getOrNull(i)?.toString() ?: "·", color = Cyan, fontSize = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !demoUnlocked && entered.size < 3, onClick = {
                    entered = entered + digit
                    digit = (digit + 1) % 10
                    message = "CIPHER INPUT"
                }) { Text("ADD $digit") }
                Button(enabled = entered.size == 3 && !demoUnlocked, onClick = {
                    if (entered == TargetSequence) {
                        demoUnlocked = true
                        message = "SEQUENCE ACCEPTED"
                    } else {
                        entered = emptyList()
                        message = "SEQUENCE REJECTED · RETRY"
                    }
                }) { Text("VERIFY") }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                entered = emptyList()
                digit = 0
                demoUnlocked = false
                message = "CORE STANDBY"
            }) { Text("RESET") }
            Spacer(Modifier.height(10.dp))
            Text("Prototype interaction only. Not device authentication.", color = Muted, fontSize = 10.sp)
        }

        Text("OFFLINE   •   NO TRACKING   •   NO ADS", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}
