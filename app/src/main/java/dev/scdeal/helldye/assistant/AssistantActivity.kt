package dev.scdeal.helldye.assistant

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scdeal.helldye.ui.AssistantScreen
import dev.scdeal.helldye.ui.SettingsActivity
import dev.scdeal.helldye.ui.theme.HelldyeTheme

class AssistantActivity : ComponentActivity() {
    private val viewModel: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            HelldyeTheme {
                val screenText by AssistInbox.screenText.collectAsStateWithLifecycle()
                val assistReceived by AssistInbox.assistReceived.collectAsStateWithLifecycle()
                val state by viewModel.state.collectAsStateWithLifecycle()

                AssistantScreen(
                    screenText = screenText,
                    assistReceived = assistReceived,
                    state = state,
                    onSubmit = viewModel::submit,
                    onAddAttachment = viewModel::addAttachment,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onNotice = viewModel::notice,
                    onDismissNotice = viewModel::dismissNotice,
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) AssistInbox.clear()
        super.onDestroy()
    }
}
