package dev.scdeal.helldye.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scdeal.helldye.gateway.GatewaySessionStore
import dev.scdeal.helldye.ui.AssistantScreen
import dev.scdeal.helldye.ui.SettingsActivity
import dev.scdeal.helldye.ui.theme.HelldyeTheme

abstract class AssistantHostActivity : ComponentActivity() {
    protected abstract val overlay: Boolean

    private val viewModel: AssistantViewModel by viewModels()
    private var keepDraftOnDestroy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (!overlay) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (overlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setBackgroundBlurRadius(48)
        }

        setContent {
            HelldyeTheme {
                val screenshot by AssistInbox.screenshot.collectAsStateWithLifecycle()
                val assistReceived by AssistInbox.assistReceived.collectAsStateWithLifecycle()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val expandAction: (() -> Unit)? = if (overlay) {
                    {
                        keepDraftOnDestroy = true
                        GatewaySessionStore.lastEventId = null
                        startActivity(
                            Intent(this@AssistantHostActivity, AssistantActivity::class.java),
                        )
                        finish()
                    }
                } else {
                    null
                }

                AssistantScreen(
                    overlay = overlay,
                    screenshot = if (overlay) screenshot else null,
                    assistReceived = assistReceived,
                    state = state,
                    onSubmit = viewModel::submit,
                    onAddAttachment = viewModel::addAttachment,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onNotice = viewModel::notice,
                    onDismissNotice = viewModel::dismissNotice,
                    onOpenSettings = {
                        startActivity(Intent(this@AssistantHostActivity, SettingsActivity::class.java))
                    },
                    onOpenModelPicker = viewModel::openModelPicker,
                    onCloseModelPicker = viewModel::closeModelPicker,
                    onSelectModel = viewModel::selectModel,
                    onNewSession = viewModel::newSession,
                    onAbort = viewModel::abort,
                    onExpand = expandAction,
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            if (overlay) {
                AssistInbox.clear()
                if (!keepDraftOnDestroy) AssistantDraftStore.clear()
            } else {
                AssistantDraftStore.clear()
            }
        }
        super.onDestroy()
    }
}
