package dev.vanta.smartlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VoidBlack = Color(0xFF06090E)
private val Cyan = Color(0xFF55D9FF)
private val Muted = Color(0xFF8294AA)


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
    var activated by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(VoidBlack).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("V A N T A   /   V O I D", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(36.dp))
            Text("00:00", color = Color.White, fontSize = 56.sp)
            Text("PERSONAL SECURITY INTERFACE", color = Muted, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(220.dp).border(1.dp, Cyan.copy(alpha = .7f), CircleShape)
                    .padding(16.dp).border(1.dp, Cyan.copy(alpha = .25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(130.dp).border(2.dp, Cyan, CircleShape)
                        .background(Cyan.copy(alpha = .04f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (activated) "ACTIVE" else "CORE", color = Cyan, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(if (activated) "INTERFACE ACTIVATED" else "CORE STANDBY", color = Cyan, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text("Prototype interface only — Android device remains protected by its system lock.", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(22.dp))
            Button(onClick = { activated = !activated }) {
                Text(if (activated) "RESET CORE" else "ACTIVATE CORE")
            }
        }
        Text("OFFLINE   •   NO TRACKING   •   NO ADS", color = Muted, fontSize = 10.sp)
    }
}
