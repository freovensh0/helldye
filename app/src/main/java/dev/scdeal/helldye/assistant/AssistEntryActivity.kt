package dev.scdeal.helldye.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.scdeal.helldye.MainActivity
import dev.scdeal.helldye.role.AssistantRole

/**
 * Entry point for the system assist intent. Devices that do not expose the assistant role use this
 * chooser target; the role path still goes through [HelldyeVoiceInteractionSession].
 */
class AssistEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = if (AssistantRole.isHeld(this)) {
            AssistantOverlayActivity::class.java
        } else {
            MainActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
