package dev.vanta.smartlock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity

private data class LaunchableApp(val label: String, val packageName: String)

class AppGuardActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == packageName) null
                else LaunchableApp(info.loadLabel(packageManager).toString(), pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        setContent {
            val selected = remember {
                mutableStateOf(AppGuardStore.protectedPackages(this@AppGuardActivity).toSet())
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF06090E)) {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("VOID APP GUARD", color = Color(0xFF55D9FF), fontSize = 22.sp)
                        Text(
                            "Choose apps VANTA should protect. App Guard uses Android Accessibility " +
                                "only to detect which app is in the foreground; it does not read screen text.",
                            color = Color(0xFFB0BEC5), fontSize = 13.sp
                        )
                        Text(
                            "Enable VANTA App Guard below after choosing apps. Android will ask you " +
                                "to grant Accessibility access explicitly.",
                            color = Color(0xFF8294AA), fontSize = 12.sp
                        )
                        LazyColumn(Modifier.weight(1f)) {
                            items(apps, key = { it.packageName }) { app ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = app.packageName in selected.value,
                                        onCheckedChange = { checked ->
                                            selected.value = if (checked) selected.value + app.packageName
                                            else selected.value - app.packageName
                                        }
                                    )
                                    Text(app.label, color = Color.White, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Button(
                            onClick = {
                                AppGuardStore.setProtectedPackages(this@AppGuardActivity, selected.value)
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("SAVE & ENABLE APP GUARD") }
                        Button(
                            onClick = {
                                AppGuardStore.setProtectedPackages(this@AppGuardActivity, selected.value)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("SAVE SELECTION") }
                    }
                }
            }
        }
    }
}
