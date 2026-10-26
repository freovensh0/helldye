package dev.scdeal.helldye.assistant

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scdeal.helldye.ui.AssistantScreen
import dev.scdeal.helldye.ui.SettingsActivity
import dev.scdeal.helldye.ui.components.HelldyeButton
import dev.scdeal.helldye.ui.components.HelldyeButtonStyle
import dev.scdeal.helldye.ui.components.Wordmark
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens
import dev.scdeal.helldye.ui.theme.HelldyeTheme

abstract class AssistantHostActivity : ComponentActivity() {
    protected abstract val overlay: Boolean

    private val viewModel: AssistantViewModel by viewModels()
    private var keepDraftOnDestroy = false
    private var unlocked by mutableStateOf(false)
    private var unlockNotice by mutableStateOf<String?>(null)
    private val keyguard: KeyguardManager? by lazy { getSystemService(KeyguardManager::class.java) }
    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // A showWhenLocked overlay stays in front when the screen turns off, so the gate
                // must close at screen-off rather than wait for the next activity resume.
                Intent.ACTION_SCREEN_OFF -> unlocked = false
                Intent.ACTION_USER_PRESENT -> unlocked = keyguard?.isKeyguardLocked != true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (overlay) {
            // The overlay must be reachable above the keyguard; content stays behind the gate.
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        // The operator asked for assist screenshots to work; FLAG_SECURE is intentionally absent.
        // Recents is still excluded, and the screenshot stays memory-only.
        unlocked = keyguard?.isKeyguardLocked != true
        ContextCompat.registerReceiver(
            this,
            lockReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            HelldyeTheme {
                if (unlocked) {
                    AssistantContent()
                } else {
                    LockedGate(
                        canUnlock = keyguard?.isDeviceSecure == true,
                        notice = unlockNotice,
                        onUnlock = ::requestUnlock,
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    @Composable
    private fun AssistantContent() {
        val screenshot by ScreenshotStager.bitmap.collectAsStateWithLifecycle()
        val screenState by ScreenshotStager.state.collectAsStateWithLifecycle()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val expandAction: (() -> Unit)? = if (overlay) {
            {
                keepDraftOnDestroy = true
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
            screenState = if (overlay) screenState else ScreenCaptureState.None,
            state = state,
            onSubmit = viewModel::submit,
            onDraftChange = viewModel::updateDraft,
            onAddAttachment = viewModel::addAttachment,
            onRemoveAttachment = viewModel::removeAttachment,
            onRemoveScreenshot = viewModel::removeScreenshot,
            onNotice = viewModel::notice,
            onDismissNotice = viewModel::dismissNotice,
            onOpenSettings = {
                startActivity(Intent(this@AssistantHostActivity, SettingsActivity::class.java))
            },
            onOpenModelPicker = viewModel::openModelPicker,
            onCloseModelPicker = viewModel::closeModelPicker,
            onSelectModel = viewModel::selectModel,
            onSetAllowEdits = viewModel::setAllowEdits,
            onOpenThinkingPicker = viewModel::openThinkingPicker,
            onCloseThinkingPicker = viewModel::closeThinkingPicker,
            onSelectThinking = viewModel::selectThinking,
            onExtensionValue = viewModel::submitExtensionValue,
            onExtensionConfirm = viewModel::submitExtensionConfirm,
            onExtensionCancel = viewModel::cancelExtension,
            onEditorPrefillConsumed = viewModel::consumeEditorPrefill,
            onNewSession = viewModel::newSession,
            onAbort = viewModel::abort,
            onCompact = viewModel::compact,
            onOpenSessions = viewModel::openSessions,
            onCloseSessions = viewModel::closeSessions,
            onSwitchSession = viewModel::switchSession,
            onDeleteSession = viewModel::deleteSession,
            onRenameSession = viewModel::renameSession,
            onLoadOlder = viewModel::loadOlder,
            onImportSessions = viewModel::importSessions,
            onOpenDiff = viewModel::openDiff,
            onCloseDiff = viewModel::closeDiff,
            onOpenTree = viewModel::openTree,
            onCloseTree = viewModel::closeTree,
            onExport = viewModel::exportHtml,
            onExportConsumed = viewModel::consumeExport,
            onReadAnswer = viewModel::readLastAnswer,
            onExpand = expandAction,
            onClose = { finish() },
        )
    }

    override fun onResume() {
        super.onResume()
        unlocked = keyguard?.isKeyguardLocked != true
    }

    override fun onStop() {
        // Backgrounding or hiding drops the capture; rotation keeps it in the stager.
        if (overlay && !isChangingConfigurations) {
            ScreenshotStager.clear()
            viewModel.clearScreenQuery()
        }
        super.onStop()
    }

    private fun requestUnlock() {
        val manager = keyguard ?: return
        if (!manager.isDeviceSecure) return
        unlockNotice = null
        manager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    unlocked = true
                }

                override fun onDismissError() {
                    unlockNotice = "Could not dismiss the screen lock. Unlock the device, then try again."
                }

                override fun onDismissCancelled() {
                    unlockNotice = "Unlock cancelled. Unlock the device, then try again."
                }
            },
        )
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            if (overlay) {
                ScreenshotStager.clear()
                viewModel.clearScreenQuery()
                if (!keepDraftOnDestroy) AssistantDraftStore.clear()
            } else {
                AssistantDraftStore.clear()
            }
        }
        runCatching { unregisterReceiver(lockReceiver) }
        super.onDestroy()
    }
}

@Composable
private fun LockedGate(
    canUnlock: Boolean,
    notice: String?,
    onUnlock: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HelldyeColors.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(HelldyeDimens.ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Wordmark()
        Spacer(Modifier.height(HelldyeDimens.Xl))
        Text(
            text = "LOCKED",
            style = MaterialTheme.typography.displayLarge,
            color = HelldyeColors.Text,
        )
        Spacer(Modifier.height(HelldyeDimens.Md))
        Text(
            text = if (canUnlock) {
                "Unlock the device to open HELLDYE."
            } else {
                "Set a device screen lock, then reopen HELLDYE."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
        )
        if (notice != null) {
            Spacer(Modifier.height(HelldyeDimens.Md))
            Text(
                text = notice,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        HelldyeDimens.Hairline,
                        HelldyeColors.SignalLine,
                        MaterialTheme.shapes.small,
                    )
                    .padding(HelldyeDimens.Md),
                style = MaterialTheme.typography.bodyMedium,
                color = HelldyeColors.Signal,
            )
        }
        Spacer(Modifier.height(HelldyeDimens.Xl))
        if (canUnlock) {
            HelldyeButton(
                text = "UNLOCK",
                onClick = onUnlock,
                style = HelldyeButtonStyle.Primary,
            )
            Spacer(Modifier.height(HelldyeDimens.Sm))
        }
        HelldyeButton(text = "CLOSE", onClick = onClose)
    }
}
