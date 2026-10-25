package dev.scdeal.helldye.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.scdeal.helldye.R
import dev.scdeal.helldye.assistant.AssistantUiState
import dev.scdeal.helldye.assistant.Attachment
import dev.scdeal.helldye.assistant.ChatMessage
import dev.scdeal.helldye.assistant.ChatRole
import dev.scdeal.helldye.assistant.ExtensionDialog
import dev.scdeal.helldye.assistant.ExtensionWidget
import dev.scdeal.helldye.assistant.ImageRules
import dev.scdeal.helldye.assistant.NoticeTone
import dev.scdeal.helldye.assistant.SlashCommand
import dev.scdeal.helldye.assistant.SlashCommands
import dev.scdeal.helldye.assistant.ToolInfo
import dev.scdeal.helldye.assistant.ToolSummary
import dev.scdeal.helldye.assistant.UsageSummary
import dev.scdeal.helldye.gateway.GatewayModel
import dev.scdeal.helldye.gateway.GatewaySession
import dev.scdeal.helldye.gateway.GatewayStats
import dev.scdeal.helldye.ui.components.SectionLabel
import dev.scdeal.helldye.ui.components.StatusChip
import dev.scdeal.helldye.ui.components.StatusTone
import dev.scdeal.helldye.ui.components.WarningBanner
import dev.scdeal.helldye.ui.components.Wordmark
import dev.scdeal.helldye.ui.markdown.MarkdownText
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens
import dev.scdeal.helldye.ui.theme.HelldyeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

@Composable
fun AssistantScreen(
    overlay: Boolean,
    screenshot: Bitmap?,
    assistReceived: Boolean,
    state: AssistantUiState,
    onSubmit: (String) -> Unit,
    onAddAttachment: (Attachment) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onNotice: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onCloseModelPicker: () -> Unit,
    onSelectModel: (GatewayModel) -> Unit,
    onOpenThinkingPicker: () -> Unit,
    onCloseThinkingPicker: () -> Unit,
    onSelectThinking: (String) -> Unit,
    onExtensionValue: (String) -> Unit,
    onExtensionConfirm: (Boolean) -> Unit,
    onExtensionCancel: () -> Unit,
    onEditorPrefillConsumed: () -> Unit,
    onNewSession: () -> Unit,
    onAbort: () -> Unit,
    onCompact: () -> Unit,
    onOpenSessions: () -> Unit,
    onCloseSessions: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExpand: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var attachmentMenu by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun runCommand(text: String): Boolean {
        when (text.trim()) {
            "/model" -> {
                input = ""
                attachmentMenu = false
                onOpenModelPicker()
                return true
            }
            "/thinking" -> {
                input = ""
                attachmentMenu = false
                onOpenThinkingPicker()
                return true
            }
            "/new" -> {
                input = ""
                attachmentMenu = false
                onNewSession()
                return true
            }
            "/abort" -> {
                input = ""
                attachmentMenu = false
                onAbort()
                return true
            }
            "/compact" -> {
                input = ""
                attachmentMenu = false
                onCompact()
                return true
            }
            "/help" -> {
                input = ""
                attachmentMenu = false
                onNotice("Commands: /model, /thinking, /new, /abort, /compact, /help")
                return true
            }
        }
        return false
    }

    fun send() {
        if (input.isBlank() && state.attachments.isEmpty()) return
        if (runCommand(input)) return
        onSubmit(input)
        input = ""
        attachmentMenu = false
        keyboard?.hide()
    }

    val slashQuery = input.trim()
    val showSlash = slashQuery.startsWith("/") && !slashQuery.contains(' ')
    val slashEntries = remember(slashQuery, state.commands) {
        SlashCommands.matches(slashQuery, state.commands)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readPhoto(context, uri) }
                    .onSuccess { onAddAttachment(it) }
                    .onFailure { onNotice(it.message ?: "Could not read the image.") }
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readTextFile(context, uri) }
                    .onSuccess { onAddAttachment(it) }
                    .onFailure { onNotice(it.message ?: "Could not read the file.") }
            }
        }
    }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraFile
        if (success && file != null) {
            scope.launch {
                runCatching { readCameraPhoto(file) }
                    .onSuccess { onAddAttachment(it) }
                    .onFailure { onNotice(it.message ?: "Could not read the photo.") }
            }
        }
    }
    fun takePhoto() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot-${System.currentTimeMillis()}.jpg")
        cameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        cameraPicker.launch(uri)
    }

    val speech = rememberSpeechInput(
        onResult = { input = it },
        onError = { onNotice(it) },
    )
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            speech.start()
        } else {
            onNotice("Microphone permission denied.")
        }
    }
    fun startVoice() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) speech.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler {
        when {
            state.extensionDialog != null -> onExtensionCancel()
            state.sessionsOpen -> onCloseSessions()
            state.thinkingPickerOpen -> onCloseThinkingPicker()
            state.modelPickerOpen -> onCloseModelPicker()
            attachmentMenu -> attachmentMenu = false
            showSlash -> input = ""
            else -> onClose()
        }
    }

    val lastMessage = state.messages.lastOrNull()
    LaunchedEffect(
        state.messages.size,
        lastMessage?.text?.length,
        lastMessage?.thinking?.length,
        lastMessage?.tool?.result?.length,
    ) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    LaunchedEffect(state.editorPrefill) {
        state.editorPrefill?.let {
            input = it
            onEditorPrefillConsumed()
        }
    }

    LaunchedEffect(state.extensionDialog?.id) {
        if (state.extensionDialog != null) keyboard?.hide()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HelldyeColors.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AssistantTopBar(
                overlay = overlay,
                screenshot = screenshot,
                assistReceived = assistReceived,
                onOpenSessions = onOpenSessions,
                onExpand = onExpand,
                onClose = onClose,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = HelldyeDimens.ScreenPadding,
                    top = HelldyeDimens.Lg,
                    end = HelldyeDimens.ScreenPadding,
                    bottom = HelldyeDimens.Md,
                ),
                verticalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        EmptyState()
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    MessageRow(message)
                }
                if (state.streaming) {
                    item {
                        Text(
                            text = "WORKING",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = HelldyeColors.Signal,
                            modifier = Modifier.padding(top = HelldyeDimens.Sm),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HelldyeColors.Black)
                    .padding(horizontal = HelldyeDimens.Md, vertical = HelldyeDimens.Sm),
            ) {
                ExtensionWidgets(state.extensionWidgets, above = true)

                when {
                    state.error != null -> WarningBanner(
                        text = state.error,
                        action = "DISMISS",
                        onAction = onDismissNotice,
                    )
                    state.notice != null && state.noticeTone != NoticeTone.Info -> WarningBanner(
                        text = state.notice,
                        action = "DISMISS",
                        onAction = onDismissNotice,
                    )
                    state.notice != null -> NoticeBar(
                        text = state.notice,
                        onDismiss = onDismissNotice,
                    )
                }
                if (state.error != null || state.notice != null) {
                    Spacer(Modifier.height(HelldyeDimens.Sm))
                }

                if (state.attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = HelldyeDimens.Sm),
                        horizontalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm),
                    ) {
                        state.attachments.forEachIndexed { index, attachment ->
                            AttachmentChip(
                                label = attachment.label,
                                onRemove = { onRemoveAttachment(index) },
                            )
                        }
                    }
                }

                if (attachmentMenu) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .background(HelldyeColors.Surface)
                            .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp))
                            .padding(HelldyeDimens.Xs)
                            .padding(bottom = HelldyeDimens.Sm),
                        horizontalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm),
                    ) {
                        AttachmentAction("PHOTO") { attachmentMenu = false; photoPicker.launch("image/*") }
                        AttachmentAction("CAMERA") { attachmentMenu = false; takePhoto() }
                        if (overlay) {
                            AttachmentAction("SCREEN") {
                                attachmentMenu = false
                                val shot = screenshot
                                if (shot == null) {
                                    onNotice("No screenshot is available for this request.")
                                } else if (state.attachments.any { it is Attachment.Photo && it.name == "SCREEN" }) {
                                    onNotice("The screen is already attached.")
                                } else {
                                    scope.launch {
                                        runCatching { readScreenshot(shot) }
                                            .onSuccess { onAddAttachment(it) }
                                            .onFailure {
                                                onNotice(it.message ?: "Could not read the screenshot.")
                                            }
                                    }
                                }
                            }
                        }
                        AttachmentAction("FILE") { attachmentMenu = false; filePicker.launch("*/*") }
                        AttachmentAction("PASTE") {
                            attachmentMenu = false
                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            val text = if (clip != null && clip.itemCount > 0) {
                                clip.getItemAt(0).coerceToText(context)?.toString()
                            } else {
                                null
                            }
                            if (text.isNullOrBlank()) {
                                onNotice("Clipboard is empty.")
                            } else {
                                onAddAttachment(Attachment.Text("clipboard.txt", text.take(128 * 1024)))
                            }
                        }
                        AttachmentAction("MODEL") {
                            attachmentMenu = false
                            onOpenModelPicker()
                        }
                        AttachmentAction("THINK") {
                            attachmentMenu = false
                            onOpenThinkingPicker()
                        }
                        AttachmentAction("SETTINGS", onOpenSettings)
                    }
                }

                if (showSlash && slashEntries.isNotEmpty()) {
                    SlashPalette(
                        entries = slashEntries,
                        onSelect = { entry ->
                            if (runCommand(entry.command)) {
                                keyboard?.hide()
                            } else {
                                input = entry.command + " "
                            }
                        },
                    )
                    Spacer(Modifier.height(HelldyeDimens.Sm))
                }

                Composer(
                    input = input,
                    onInputChange = { input = it },
                    onSend = ::send,
                    onStop = onAbort,
                    onVoice = ::startVoice,
                    streaming = state.streaming,
                    sendEnabled = input.isNotBlank() || state.attachments.isNotEmpty(),
                    onToggleAttachments = { attachmentMenu = !attachmentMenu },
                )

                UsageLine(
                    model = state.currentModel?.name,
                    thinking = state.thinkingLevel,
                    stats = state.stats,
                    statuses = buildList {
                        addAll(state.extensionStatus.values)
                        if (state.compacting) add("COMPACTING")
                        if (state.queued > 0) add("QUEUED ${state.queued}")
                    },
                )

                ExtensionWidgets(state.extensionWidgets, above = false)
            }
        }

        if (state.sessionsOpen) {
            SessionPicker(
                sessions = state.sessions,
                currentId = state.sessionId,
                loading = state.sessionsLoading,
                onSwitch = onSwitchSession,
                onDelete = onDeleteSession,
                onRename = onRenameSession,
                onNew = onNewSession,
                onClose = onCloseSessions,
            )
        }

        if (state.modelPickerOpen) {
            ModelPicker(
                models = state.models,
                current = state.currentModel,
                loading = state.capabilitiesLoading,
                error = state.error,
                onSelect = onSelectModel,
                onClose = onCloseModelPicker,
            )
        }

        if (state.thinkingPickerOpen) {
            ThinkingPicker(
                levels = state.thinkingLevels,
                current = state.thinkingLevel,
                loading = state.capabilitiesLoading,
                onSelect = onSelectThinking,
                onClose = onCloseThinkingPicker,
            )
        }

        state.extensionDialog?.let { dialog ->
            ExtensionDialogCard(
                dialog = dialog,
                onValue = onExtensionValue,
                onConfirm = onExtensionConfirm,
                onCancel = onExtensionCancel,
            )
        }
    }
}

@Composable
private fun AssistantTopBar(
    overlay: Boolean,
    screenshot: Bitmap?,
    assistReceived: Boolean,
    onOpenSessions: () -> Unit,
    onExpand: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val label = when {
        !overlay -> "SESSION"
        screenshot != null -> "SCREEN READY"
        assistReceived -> "SCREEN EMPTY"
        else -> "NO ASSIST"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HelldyeDimens.TopBar)
            .padding(horizontal = HelldyeDimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark()
        Spacer(Modifier.weight(1f))
        StatusChip(
            label = label,
            tone = if (screenshot != null || !overlay) StatusTone.Active else StatusTone.Neutral,
        )
        IconButton(
            onClick = onOpenSessions,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sessions),
                contentDescription = "Sessions",
                tint = HelldyeColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (onExpand != null) {
            IconButton(
                onClick = onExpand,
                modifier = Modifier.size(HelldyeDimens.TouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_expand),
                    contentDescription = "Open session in HELLDYE",
                    tint = HelldyeColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Close assistant",
                tint = HelldyeColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp)
            .background(HelldyeColors.Surface, RoundedCornerShape(2.dp))
            .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp))
            .padding(HelldyeDimens.Md),
    ) {
        SectionLabel("ASSISTANT")
        Spacer(Modifier.height(HelldyeDimens.Sm))
        Text(
            text = "READY",
            style = MaterialTheme.typography.displayLarge,
            color = HelldyeColors.Text,
        )
        Spacer(Modifier.height(HelldyeDimens.Sm))
        Text(
            text = "Attach a photo, the current screen, or a file when you need context.",
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
        )
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    message.tool?.let {
        ToolCard(message.id, it)
        return
    }
    val label = when (message.role) {
        ChatRole.User -> "YOU"
        ChatRole.Assistant -> "HELLDYE"
        ChatRole.Tool -> "TOOL"
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HelldyeColors.TextMuted,
        )
        if (message.thinking.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            ThinkingBlock(message.id, message.thinking)
        }
        if (message.text.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (message.role == ChatRole.Assistant) {
                            HelldyeColors.Surface
                        } else {
                            HelldyeColors.SurfaceRaised
                        },
                        shape = RoundedCornerShape(2.dp),
                    )
                    .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp))
                    .padding(HelldyeDimens.Md),
            ) {
                if (message.role == ChatRole.Assistant) {
                    MarkdownText(message.text)
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = HelldyeColors.Text,
                    )
                }
            }
        } else if (message.thinking.isBlank()) {
            Text(
                text = "…",
                style = MaterialTheme.typography.bodyLarge,
                color = HelldyeColors.TextMuted,
            )
        }
    }
}

@Composable
private fun ThinkingBlock(id: String, text: String) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelldyeColors.SurfaceRaised, RoundedCornerShape(2.dp))
            .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp))
            .clickable { expanded = !expanded }
            .padding(HelldyeDimens.Md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("THINKING")
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "HIDE" else "SHOW",
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextMuted,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToolCard(id: String, tool: ToolInfo) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val status = when {
        tool.running -> "RUNNING"
        tool.failed -> "FAILED"
        else -> "DONE"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelldyeColors.Surface, RoundedCornerShape(2.dp))
            .border(
                width = 1.dp,
                color = if (tool.failed) HelldyeColors.SignalLine else HelldyeColors.LineSoft,
                shape = RoundedCornerShape(2.dp),
            )
            .clickable { expanded = !expanded }
            .padding(HelldyeDimens.Md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tool.name.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = HelldyeColors.Text,
            )
            Spacer(Modifier.weight(1f))
            tool.durationMs?.let {
                Text(
                    text = ToolSummary.duration(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                )
                Spacer(Modifier.width(HelldyeDimens.Sm))
            }
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (tool.failed) HelldyeColors.Signal else HelldyeColors.TextMuted,
            )
        }
        val line = ToolSummary.line(tool)
        if (line.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            if (tool.args.isNotBlank()) {
                Spacer(Modifier.height(HelldyeDimens.Sm))
                SectionLabel("ARGS")
                Text(
                    text = tool.args,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = HelldyeColors.TextSecondary,
                    maxLines = 16,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tool.result.isNotBlank()) {
                Spacer(Modifier.height(HelldyeDimens.Sm))
                SectionLabel("RESULT")
                Text(
                    text = tool.result,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = HelldyeColors.TextSecondary,
                    maxLines = 40,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (expanded) "HIDE" else "DETAILS",
            style = MaterialTheme.typography.labelSmall,
            color = HelldyeColors.TextMuted,
        )
    }
}

@Composable
private fun AttachmentChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = HelldyeDimens.TouchTarget)
            .background(HelldyeColors.Surface, RoundedCornerShape(2.dp))
            .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
            .padding(start = HelldyeDimens.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.take(22),
            style = MaterialTheme.typography.labelMedium,
            color = HelldyeColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Remove attachment",
                tint = HelldyeColors.TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun AttachmentAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = HelldyeDimens.TouchTarget)
            .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp)),
        contentPadding = PaddingValues(horizontal = HelldyeDimens.Md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HelldyeColors.TextSecondary,
        )
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp))
            .padding(start = HelldyeDimens.Md, top = 6.dp, bottom = 6.dp, end = HelldyeDimens.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
        ) {
            Text(
                text = "DISMISS",
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
            )
        }
    }
}

@Composable
private fun UsageLine(
    model: String?,
    thinking: String?,
    stats: GatewayStats?,
    statuses: List<String>,
) {
    val text = UsageSummary.text(model, thinking, stats, statuses)
    if (text.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = HelldyeDimens.Xs, start = HelldyeDimens.Xs, end = HelldyeDimens.Xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
            color = HelldyeColors.TextMuted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ExtensionWidgets(widgets: Map<String, ExtensionWidget>, above: Boolean) {
    val blocks = widgets.values.filter { it.above == above }
    if (blocks.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HelldyeDimens.Xs)
            .padding(top = if (above) 0.dp else HelldyeDimens.Sm),
    ) {
        blocks.forEach { widget ->
            widget.lines.take(12).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SlashPalette(
    entries: List<SlashCommand>,
    onSelect: (SlashCommand) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .background(HelldyeColors.Surface, RoundedCornerShape(2.dp))
            .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp)),
    ) {
        items(entries, key = { it.command }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(entry) }
                    .heightIn(min = HelldyeDimens.TouchTarget)
                    .padding(horizontal = HelldyeDimens.Md, vertical = HelldyeDimens.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.command,
                    style = MaterialTheme.typography.labelMedium,
                    color = HelldyeColors.Text,
                )
                Spacer(Modifier.width(HelldyeDimens.Md))
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SessionPicker(
    sessions: List<GatewaySession>,
    currentId: String?,
    loading: Boolean,
    onSwitch: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    var renaming by remember { mutableStateOf<GatewaySession?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var name by remember(renaming?.id) { mutableStateOf(renaming?.name.orEmpty()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HelldyeDimens.Lg)
                .background(HelldyeColors.Black, RoundedCornerShape(2.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
                .padding(HelldyeDimens.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("SESSIONS")
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                ) {
                    Text(
                        text = "CLOSE",
                        style = MaterialTheme.typography.labelMedium,
                        color = HelldyeColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(HelldyeDimens.Sm))
            AttachmentAction("NEW SESSION", onNew)
            Spacer(Modifier.height(HelldyeDimens.Sm))

            when {
                loading && sessions.isEmpty() -> Text(
                    text = "LOADING",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = HelldyeColors.TextMuted,
                )
                sessions.isEmpty() -> Text(
                    text = "No sessions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HelldyeColors.TextMuted,
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        val current = session.id == currentId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = HelldyeDimens.Sm)
                                .background(
                                    color = if (current) HelldyeColors.SurfaceRaised else HelldyeColors.Surface,
                                    shape = RoundedCornerShape(2.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (current) HelldyeColors.LineStrong else HelldyeColors.LineSoft,
                                    shape = RoundedCornerShape(2.dp),
                                )
                                .padding(HelldyeDimens.Md),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onSwitch(session.id) },
                                ) {
                                    Text(
                                        text = session.name.ifBlank { session.id.take(8) },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = HelldyeColors.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(session.status.ifBlank { "idle" })
                                            append(" · ")
                                            append(session.id.take(8))
                                            if (current) append(" · CURRENT")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HelldyeColors.TextMuted,
                                        maxLines = 1,
                                    )
                                }
                                if (deleting == session.id) {
                                    TextButton(onClick = { deleting = null }) {
                                        Text(
                                            text = "KEEP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HelldyeColors.TextSecondary,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            deleting = null
                                            onDelete(session.id)
                                        },
                                    ) {
                                        Text(
                                            text = "DELETE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HelldyeColors.Signal,
                                        )
                                    }
                                } else {
                                    TextButton(onClick = { renaming = session }) {
                                        Text(
                                            text = "RENAME",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HelldyeColors.TextSecondary,
                                        )
                                    }
                                    TextButton(onClick = { deleting = session.id }) {
                                        Text(
                                            text = "×",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = HelldyeColors.TextMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        renaming?.let { session ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(HelldyeDimens.Lg)
                        .background(HelldyeColors.Black, RoundedCornerShape(2.dp))
                        .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
                        .padding(HelldyeDimens.Md),
                ) {
                    SectionLabel("RENAME SESSION")
                    Spacer(Modifier.height(HelldyeDimens.Sm))
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp))
                            .padding(HelldyeDimens.Sm),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = HelldyeColors.Text),
                        cursorBrush = SolidColor(HelldyeColors.White),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(HelldyeDimens.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm)) {
                        AttachmentAction("CANCEL") { renaming = null }
                        AttachmentAction("SAVE") {
                            renaming = null
                            onRename(session.id, name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPicker(
    models: List<GatewayModel>,
    current: GatewayModel?,
    loading: Boolean,
    error: String?,
    onSelect: (GatewayModel) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HelldyeDimens.Lg)
                .background(HelldyeColors.Black, RoundedCornerShape(2.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
                .padding(HelldyeDimens.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("MODEL")
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                ) {
                    Text(
                        text = "CLOSE",
                        style = MaterialTheme.typography.labelMedium,
                        color = HelldyeColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(HelldyeDimens.Sm))
            when {
                loading && models.isEmpty() -> Text(
                    text = "LOADING",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = HelldyeColors.TextMuted,
                )
                models.isEmpty() -> WarningBanner(
                    text = error ?: "No models are available on this gateway.",
                )
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm),
                ) {
                    items(models, key = { "${it.provider}/${it.id}" }) { model ->
                        val active = model.provider == current?.provider && model.id == current.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(model) }
                                .background(
                                    color = if (active) HelldyeColors.SurfaceRaised else HelldyeColors.Surface,
                                    shape = RoundedCornerShape(2.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) HelldyeColors.LineStrong else HelldyeColors.LineSoft,
                                    shape = RoundedCornerShape(2.dp),
                                )
                                .padding(HelldyeDimens.Md),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = HelldyeColors.Text,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (active) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HelldyeColors.TextSecondary,
                                    )
                                }
                            }
                            Text(
                                text = buildString {
                                    append(model.provider)
                                    if (model.contextWindow > 0) {
                                        append("  ")
                                        append(model.contextWindow / 1000)
                                        append("K")
                                    }
                                    if (model.reasoning) append("  REASONING")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = HelldyeColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingPicker(
    levels: List<String>,
    current: String?,
    loading: Boolean,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HelldyeDimens.Lg)
                .background(HelldyeColors.Black, RoundedCornerShape(2.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
                .padding(HelldyeDimens.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("THINKING")
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                ) {
                    Text(
                        text = "CLOSE",
                        style = MaterialTheme.typography.labelMedium,
                        color = HelldyeColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(HelldyeDimens.Sm))
            when {
                loading && levels.isEmpty() -> Text(
                    text = "LOADING",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = HelldyeColors.TextMuted,
                )
                levels.isEmpty() -> Text(
                    text = "No thinking levels for this model.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HelldyeColors.TextMuted,
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(levels, key = { it }) { level ->
                        val active = level == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(level) }
                                .heightIn(min = HelldyeDimens.TouchTarget)
                                .padding(horizontal = HelldyeDimens.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = level.uppercase(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (active) HelldyeColors.Text else HelldyeColors.TextSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HelldyeColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionDialogCard(
    dialog: ExtensionDialog,
    onValue: (String) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(dialog.id) {
        mutableStateOf(
            when (dialog) {
                is ExtensionDialog.Editor -> dialog.prefill
                else -> ""
            },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HelldyeDimens.Lg)
                .background(HelldyeColors.Black, RoundedCornerShape(2.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
                .padding(HelldyeDimens.Md),
        ) {
            SectionLabel("EXTENSION")
            Spacer(Modifier.height(HelldyeDimens.Xs))
            Text(
                text = dialog.title.ifBlank { "Extension request" },
                style = MaterialTheme.typography.bodyLarge,
                color = HelldyeColors.Text,
            )
            Spacer(Modifier.height(HelldyeDimens.Sm))
            when (dialog) {
                is ExtensionDialog.Select -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(dialog.options, key = { it }) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onValue(option) }
                                    .heightIn(min = HelldyeDimens.TouchTarget)
                                    .padding(horizontal = HelldyeDimens.Sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = HelldyeColors.Text,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(HelldyeDimens.Sm))
                    AttachmentAction("CANCEL", onCancel)
                }
                is ExtensionDialog.Confirm -> {
                    if (dialog.message.isNotBlank()) {
                        Text(
                            text = dialog.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HelldyeColors.TextSecondary,
                        )
                        Spacer(Modifier.height(HelldyeDimens.Sm))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm)) {
                        AttachmentAction("CANCEL", onCancel)
                        AttachmentAction("NO") { onConfirm(false) }
                        AttachmentAction("YES") { onConfirm(true) }
                    }
                }
                is ExtensionDialog.Input, is ExtensionDialog.Editor -> {
                    val editor = dialog is ExtensionDialog.Editor
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (editor) 120.dp else HelldyeDimens.TouchTarget)
                            .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp))
                            .padding(HelldyeDimens.Sm),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = HelldyeColors.Text),
                        cursorBrush = SolidColor(HelldyeColors.White),
                        maxLines = if (editor) 10 else 4,
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        text = (dialog as? ExtensionDialog.Input)?.placeholder.orEmpty(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = HelldyeColors.TextMuted,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.height(HelldyeDimens.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm)) {
                        AttachmentAction("CANCEL", onCancel)
                        AttachmentAction("SEND") { onValue(text) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit,
    streaming: Boolean,
    sendEnabled: Boolean,
    onToggleAttachments: () -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelldyeColors.Black, shape)
            .border(1.dp, HelldyeColors.LineStrong, shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleAttachments,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Add attachment",
                tint = HelldyeColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = HelldyeDimens.Sm),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = HelldyeColors.Text),
            cursorBrush = SolidColor(HelldyeColors.White),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (input.isEmpty()) {
                        Text(
                            text = "Ask HELLDYE",
                            style = MaterialTheme.typography.bodyLarge,
                            color = HelldyeColors.TextMuted,
                        )
                    }
                    inner()
                }
            },
        )
        IconButton(
            onClick = onVoice,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Voice input",
                tint = HelldyeColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = if (streaming) onStop else onSend,
            enabled = streaming || sendEnabled,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(if (streaming) R.drawable.ic_stop else R.drawable.ic_send),
                contentDescription = if (streaming) "Stop" else "Send",
                tint = if (streaming || sendEnabled) HelldyeColors.Text else HelldyeColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private suspend fun readPhoto(context: Context, uri: Uri): Attachment.Photo = withContext(Dispatchers.IO) {
    val name = displayName(context, uri) ?: "photo"
    readImageAttachment(
        name = name,
        source = ImageDecoder.createSource(context.contentResolver, uri),
    )
}

private suspend fun readCameraPhoto(file: File): Attachment.Photo = withContext(Dispatchers.IO) {
    readImageAttachment(name = "CAMERA", source = ImageDecoder.createSource(file))
}

private suspend fun readScreenshot(bitmap: Bitmap): Attachment.Photo = withContext(Dispatchers.IO) {
    val size = ImageRules.scaledSize(bitmap.width, bitmap.height)
    val scaled = if (size.first != bitmap.width || size.second != bitmap.height) {
        Bitmap.createScaledBitmap(bitmap, size.first, size.second, true)
    } else {
        bitmap
    }
    try {
        encodeJpeg("SCREEN", scaled)
    } finally {
        if (scaled !== bitmap) scaled.recycle()
    }
}

/** Decodes with the platform decoder (which applies EXIF orientation), then re-encodes as JPEG. */
private fun readImageAttachment(
    name: String,
    source: ImageDecoder.Source,
): Attachment.Photo {
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val size = ImageRules.scaledSize(info.size.width, info.size.height)
        decoder.setTargetSize(size.first, size.second)
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
    }
    return try {
        encodeJpeg(name, bitmap)
    } finally {
        bitmap.recycle()
    }
}

private fun encodeJpeg(name: String, bitmap: Bitmap): Attachment.Photo {
    var current = bitmap
    var owned = false
    var bytes = ByteArray(0)
    var attempts = 0
    while (true) {
        val output = ByteArrayOutputStream()
        current.compress(Bitmap.CompressFormat.JPEG, ImageRules.JPEG_QUALITY, output)
        bytes = output.toByteArray()
        if (bytes.size <= ImageRules.MAX_BYTES_PER_IMAGE || attempts >= 4) break
        val next = Bitmap.createScaledBitmap(
            current,
            ((current.width * 3) / 4).coerceAtLeast(1),
            ((current.height * 3) / 4).coerceAtLeast(1),
            true,
        )
        if (owned) current.recycle()
        current = next
        owned = true
        attempts++
    }
    if (owned) current.recycle()
    return Attachment.Photo(
        name = name,
        mimeType = "image/jpeg",
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

private suspend fun readTextFile(context: Context, uri: Uri): Attachment.Text = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { readLimited(it, 128 * 1024) }
        ?: error("Could not open the file.")
    Attachment.Text(
        name = displayName(context, uri) ?: "file",
        content = bytes.toString(Charsets.UTF_8),
    )
}

private fun readLimited(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) error("Attachment is larger than ${limit / 1024} KB.")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun displayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getString(0)
        }
    }
    return uri.lastPathSegment
}

@Preview(showBackground = false, backgroundColor = 0x00000000)
@Composable
private fun AssistantScreenPreview() {
    HelldyeTheme {
        AssistantScreen(
            overlay = true,
            screenshot = null,
            assistReceived = true,
            state = AssistantUiState(
                messages = listOf(
                    ChatMessage("u", ChatRole.User, "What changed in my battery settings?"),
                    ChatMessage("a", ChatRole.Assistant, "Battery usage is down 8% since this morning."),
                    ChatMessage("t", ChatRole.Tool, "bash DONE"),
                ),
            ),
            onSubmit = {},
            onAddAttachment = {},
            onRemoveAttachment = {},
            onNotice = {},
            onDismissNotice = {},
            onOpenSettings = {},
            onOpenModelPicker = {},
            onCloseModelPicker = {},
            onSelectModel = {},
            onOpenThinkingPicker = {},
            onCloseThinkingPicker = {},
            onSelectThinking = {},
            onExtensionValue = {},
            onExtensionConfirm = {},
            onExtensionCancel = {},
            onEditorPrefillConsumed = {},
            onNewSession = {},
            onAbort = {},
            onCompact = {},
            onOpenSessions = {},
            onCloseSessions = {},
            onSwitchSession = {},
            onDeleteSession = {},
            onRenameSession = { _, _ -> },
            onExpand = {},
            onClose = {},
        )
    }
}
