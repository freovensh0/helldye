package dev.scdeal.helldye

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.scdeal.helldye.assistant.AssistantActivity
import dev.scdeal.helldye.gateway.SettingsStore
import dev.scdeal.helldye.role.AssistantRole
import dev.scdeal.helldye.ui.OnboardingScreen
import dev.scdeal.helldye.ui.SettingsActivity
import dev.scdeal.helldye.ui.theme.HelldyeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            HelldyeTheme {
                val activity = this@MainActivity
                val settings = remember { SettingsStore(activity) }
                var roleHeld by remember { mutableStateOf(AssistantRole.isHeld(activity)) }
                var gatewayConfigured by remember { mutableStateOf(settings.isConfigured()) }
                val roleAvailable = remember { AssistantRole.isAvailable(activity) }
                val lifecycleOwner = LocalLifecycleOwner.current
                val roleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    roleHeld = AssistantRole.isHeld(activity)
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            roleHeld = AssistantRole.isHeld(activity)
                            gatewayConfigured = settings.isConfigured()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                OnboardingScreen(
                    roleAvailable = roleAvailable,
                    roleHeld = roleHeld,
                    gatewayConfigured = gatewayConfigured,
                    onRequestRole = {
                        AssistantRole.requestIntent(activity)?.let(roleLauncher::launch)
                    },
                    onOpenAssistantSettings = {
                        AssistantRole.openSystemSettings(activity)
                    },
                    onOpenGatewaySettings = {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java))
                    },
                    onOpenAssistant = {
                        activity.startActivity(Intent(activity, AssistantActivity::class.java))
                    },
                )
            }
        }
    }
}
