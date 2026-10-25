package dev.scdeal.helldye.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
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
import dev.scdeal.helldye.assistant.Conversation
import dev.scdeal.helldye.assistant.ConversationItem
import dev.scdeal.helldye.assistant.ExtensionDialog
import dev.scdeal.helldye.assistant.ExtensionWidget
import dev.scdeal.helldye.assistant.ImageRules
import dev.scdeal.helldye.assistant.NoticeTone
import dev.scdeal.helldye.assistant.ScreenCaptureState
import dev.scdeal.helldye.assistant.SlashCommand
import dev.scdeal.helldye.assistant.SlashCommands
import dev.scdeal.helldye.assistant.ToolInfo
import dev.scdeal.helldye.assistant.ToolSummary
import dev.scdeal.helldye.assistant.UsageSummary
import dev.scdeal.helldye.gateway.GatewayModel
import dev.scdeal.helldye.gateway.GatewaySession
import dev.scdeal.helldye.ui.components.Hairline
import dev.scdeal.helldye.ui.components.SectionLabel
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
import java.util.Locale

@Composable
fun AssistantScreen(
    overlay: Boolean,
    screenshot: Bitmap?,
    screenState: ScreenCaptureState,
    state: AssistantUiState,
    onSubmit: (String) -> Unit,
    onAddAttachment: (Attachment) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onRemoveScreenshot: () -> Unit,
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
    onLoadOlder: () -> Unit,
    onExpand: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var attachmentMenu by rememberSaveable { mutableStateOf(false) }
    var overflowOpen by rememberSaveable { mutableStateOf(false) }
    var pinnedToBottom by rememberSaveable { mutableStateOf(true) }
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
        if (input.isBlank() && state.attachments.isEmpty() &&
            screenState != ScreenCaptureState.Ready
        ) {
            return
        }
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
            overflowOpen -> overflowOpen = false
            attachmentMenu -> attachmentMenu = false
            showSlash -> input = ""
            else -> onClose()
        }
    }

    val conversation = remember(state.messages, state.screenMessages) {
        Conversation.items(state.messages + state.screenMessages)
    }
    val railState = when {
        state.screenQuery -> "TEMP"
        state.stopping -> "STOPPING"
        state.streaming -> "THINKING"
        screenState == ScreenCaptureState.Ready || screenState == ScreenCaptureState.Sent -> "TEMP"
        else -> "READY"
    }
    val lastMessage = (state.messages + state.screenMessages).lastOrNull()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                pinnedToBottom = Conversation.shouldPinToBottom(
                    lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                    totalItems = listState.layoutInfo.totalItemsCount,
                )
            }
        }
    }
    LaunchedEffect(
        conversation.size,
        lastMessage?.text?.length,
        lastMessage?.thinking?.length,
        lastMessage?.tool?.result?.length,
    ) {
        if (conversation.isNotEmpty() && pinnedToBottom) {
            listState.scrollToItem(conversation.lastIndex)
        }
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = HelldyeDimens.ContentMaxWidth)
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                TopRail(
                    sessionId = state.sessionId,
                    stateLabel = railState,
                    closeLabel = if (overlay) "Close assistant" else "Back",
                    onOpenOverflow = { overflowOpen = true },
                    onClose = onClose,
                )
                Hairline(color = HelldyeColors.LineSoft)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = HelldyeDimens.ScreenPadding,
                            top = HelldyeDimens.Lg,
                            end = HelldyeDimens.ScreenPadding,
                            bottom = HelldyeDimens.Md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(HelldyeDimens.Lg),
                    ) {
                        if (state.hasOlder) {
                            item(key = "older") {
                                TextButton(
                                    onClick = onLoadOlder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = HelldyeDimens.TouchTarget),
                                ) {
                                    Text(
                                        text = if (state.loadingOlder) "LOADING…" else "LOAD OLDER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HelldyeColors.TextSecondary,
                                    )
                                }
                            }
                        }
                        if (state.messages.isEmpty() && state.screenMessages.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    overlay = overlay,
                                    screenState = screenState,
                                )
                            }
                        }
                        items(
                            items = conversation,
                            key = { it.key },
                            contentType = { if (it is ConversationItem.Tools) "tools" else "turn" },
                        ) { item ->
                            when (item) {
                                is ConversationItem.Turn -> TurnBlock(item.message, state.streaming)
                                is ConversationItem.Tools -> ToolGroupBlock(item)
                            }
                        }
                        if (state.streaming && lastMessage?.role == ChatRole.User) {
                            item(key = "waiting") {
                                WaitingAnswer()
                            }
                        }
                    }

                    if (!pinnedToBottom && conversation.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch { listState.animateScrollToItem(conversation.lastIndex) }
                                pinnedToBottom = true
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = HelldyeDimens.Md)
                                .heightIn(min = HelldyeDimens.TouchTarget),
                        ) {
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = HelldyeColors.TextSecondary,
                            )
                        }
                    }
                }

                BottomPanel(
                    state = state,
                    screenshot = screenshot,
                    screenState = screenState,
                    attachmentMenu = attachmentMenu,
                    showSlash = showSlash,
                    slashEntries = slashEntries,
                    input = input,
                    onInputChange = { input = it },
                    onSend = ::send,
                    onStop = onAbort,
                    onVoice = ::startVoice,
                    onToggleAttachments = {
                        attachmentMenu = !attachmentMenu
                        if (attachmentMenu) keyboard?.hide()
                    },
                    onPhoto = {
                        attachmentMenu = false
                        photoPicker.launch("image/*")
                    },
                    onCamera = {
                        attachmentMenu = false
                        takePhoto()
                    },
                    onFile = {
                        attachmentMenu = false
                        filePicker.launch("*/*")
                    },
                    onPaste = {
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
                    },
                    onRemoveAttachment = onRemoveAttachment,
                    onRemoveScreenshot = onRemoveScreenshot,
                    onDismissNotice = onDismissNotice,
                    onOpenModelPicker = onOpenModelPicker,
                    onSlashSelect = { entry ->
                        if (runCommand(entry.command)) {
                            keyboard?.hide()
                        } else {
                            input = entry.command + " "
                        }
                    },
                )
            }
        }

        if (overflowOpen) {
            OverflowMenu(
                onOpenSessions = onOpenSessions,
                onOpenSettings = onOpenSettings,
                onExpand = onExpand,
                onDismiss = { overflowOpen = false },
            )
        }

        if (state.sessionsOpen) {
            SessionSheet(
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
                thinking = state.thinkingLevel,
                onOpenThinking = {
                    onCloseModelPicker()
                    onOpenThinkingPicker()
                },
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
private fun TopRail(
    sessionId: String?,
    stateLabel: String,
    closeLabel: String,
    onOpenOverflow: () -> Unit,
    onClose: () -> Unit,
) {
    val sessionTitle = sessionId
        ?.take(4)
        ?.uppercase(Locale.ROOT)
        ?.let { "SESSION $it" }
        ?: "NO SESSION"
    val stateColor = when (stateLabel) {
        "THINKING" -> HelldyeColors.Text
        "TEMP" -> HelldyeColors.TextMuted
        else -> HelldyeColors.TextSecondary
    }
    val showWordmark = LocalDensity.current.fontScale < 1.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HelldyeDimens.TopBar)
            .padding(horizontal = HelldyeDimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showWordmark) {
            Wordmark()
            Spacer(Modifier.width(HelldyeDimens.Lg))
        }
        Text(
            text = sessionTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(HelldyeDimens.Sm))
        Text(
            text = stateLabel,
            style = MaterialTheme.typography.labelMedium,
            color = stateColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(
            onClick = onOpenOverflow,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more),
                contentDescription = "More actions",
                tint = HelldyeColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = closeLabel,
                tint = HelldyeColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun OverflowMenu(
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit,
    onExpand: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = HelldyeDimens.TopBar, end = HelldyeDimens.Sm)
                .width(208.dp)
                .background(HelldyeColors.Black, RoundedCornerShape(2.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp)),
        ) {
            OverflowRow("SESSIONS") {
                onDismiss()
                onOpenSessions()
            }
            Hairline(color = HelldyeColors.LineSoft)
            OverflowRow("SETTINGS") {
                onDismiss()
                onOpenSettings()
            }
            if (onExpand != null) {
                Hairline(color = HelldyeColors.LineSoft)
                OverflowRow("OPEN SESSION") {
                    onDismiss()
                    onExpand()
                }
            }
        }
    }
}

@Composable
private fun OverflowRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HelldyeDimens.Lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HelldyeColors.Text,
        )
    }
}

@Composable
private fun EmptyState(
    overlay: Boolean,
    screenState: ScreenCaptureState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HelldyeDimens.Xl),
    ) {
        Text(
            text = "READY",
            style = MaterialTheme.typography.displayLarge,
            color = HelldyeColors.Text,
        )
        Spacer(Modifier.height(HelldyeDimens.Md))
        Text(
            text = "Ask about the screen, or type.",
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
        )
        Spacer(Modifier.height(HelldyeDimens.Xs))
        Text(
            text = when {
                !overlay -> "Nothing leaves this phone unless you send it."
                screenState == ScreenCaptureState.Ready ->
                    "One screenshot is staged. It is sent only when you send."
                screenState == ScreenCaptureState.Blocked ->
                    "This screen does not allow screenshots."
                screenState == ScreenCaptureState.Off ->
                    "No screenshot is available for this request."
                else -> "Screenshots stay on this phone until you send."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextMuted,
        )
    }
}

@Composable
private fun TurnBlock(message: ChatMessage, streaming: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        if (message.thinking.isNotBlank()) {
            ThinkingRow(message.id, message.thinking)
        }
        when (message.role) {
            ChatRole.User -> UserTurn(message.text)
            ChatRole.Assistant -> AssistantTurn(message, streaming)
            ChatRole.Tool -> message.tool?.let { ToolRow(message.id, it) }
        }
    }
}

@Composable
private fun UserTurn(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = HelldyeColors.Text,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = HelldyeColors.White,
                    size = Size(2.dp.toPx(), size.height),
                )
            }
            .padding(start = 14.dp),
    )
}

@Composable
private fun AssistantTurn(message: ChatMessage, streaming: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
    ) {
        if (message.text.isNotBlank()) {
            MarkdownText(message.text)
        } else if (streaming) {
            WaitingAnswer()
        }
    }
}

@Composable
private fun WaitingAnswer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = HelldyeColors.Text,
            trackColor = HelldyeColors.Line,
        )
    }
}

@Composable
private fun ThinkingRow(id: String, text: String) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val lines = remember(text) {
        text.lineSequence().count { it.isNotBlank() }.coerceAtLeast(1)
    }

    Column(Modifier.fillMaxWidth()) {
        Hairline(color = HelldyeColors.LineSoft)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HelldyeDimens.TouchTarget)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Hide thinking" else "Show thinking",
                ) { expanded = !expanded }
                .padding(vertical = HelldyeDimens.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "THINKING",
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
            )
            Spacer(Modifier.width(HelldyeDimens.Sm))
            Text(
                text = "· $lines ${if (lines == 1) "LINE" else "LINES"}",
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "HIDE" else "SHOW",
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
            )
        }
        if (expanded) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = HelldyeColors.TextMuted,
                modifier = Modifier.padding(bottom = HelldyeDimens.Sm),
            )
        }
    }
}

@Composable
private fun ToolRow(id: String, tool: ToolInfo) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val status = when {
        tool.running -> "RUNNING"
        tool.failed -> "FAILED"
        else -> "DONE"
    }
    val line = ToolSummary.line(tool)

    Column(Modifier.fillMaxWidth()) {
        Hairline(color = HelldyeColors.LineSoft)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HelldyeDimens.TouchTarget)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Hide tool details" else "Show tool details",
                ) { expanded = !expanded }
                .padding(vertical = HelldyeDimens.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tool.name.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelMedium,
                color = HelldyeColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(HelldyeDimens.Sm))
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            tool.durationMs?.let {
                Spacer(Modifier.width(HelldyeDimens.Sm))
                Text(
                    text = ToolSummary.duration(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                )
            }
            Spacer(Modifier.width(HelldyeDimens.Sm))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (tool.failed) HelldyeColors.Signal else HelldyeColors.TextMuted,
            )
        }
        if (expanded) {
            if (tool.args.isNotBlank()) {
                Text(
                    text = "ARGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                    modifier = Modifier.padding(top = HelldyeDimens.Sm),
                )
                Text(
                    text = tool.args,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = HelldyeColors.TextSecondary,
                    maxLines = 16,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tool.result.isNotBlank()) {
                Text(
                    text = "RESULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                    modifier = Modifier.padding(top = HelldyeDimens.Sm),
                )
                Text(
                    text = tool.result,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = HelldyeColors.TextSecondary,
                    maxLines = 40,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(HelldyeDimens.Xs))
        }
    }
}

@Composable
private fun ToolGroupBlock(item: ConversationItem.Tools) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val tools = item.messages.mapNotNull { it.tool }

    Column(Modifier.fillMaxWidth()) {
        Hairline(color = HelldyeColors.LineSoft)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HelldyeDimens.TouchTarget)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Hide tools" else "Show tools",
                ) { expanded = !expanded }
                .padding(vertical = HelldyeDimens.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Conversation.toolSummary(tools),
                style = MaterialTheme.typography.labelMedium,
                color = if (tools.any { it.failed }) HelldyeColors.Signal else HelldyeColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "HIDE" else "SHOW",
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
            )
        }
        if (expanded) {
            item.messages.forEach { message ->
                message.tool?.let { ToolRow(message.id, it) }
            }
        }
    }
}

@Composable
private fun BottomPanel(
    state: AssistantUiState,
    screenshot: Bitmap?,
    screenState: ScreenCaptureState,
    attachmentMenu: Boolean,
    showSlash: Boolean,
    slashEntries: List<SlashCommand>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit,
    onToggleAttachments: () -> Unit,
    onPhoto: () -> Unit,
    onCamera: () -> Unit,
    onFile: () -> Unit,
    onPaste: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onRemoveScreenshot: () -> Unit,
    onDismissNotice: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onSlashSelect: (SlashCommand) -> Unit,
) {
    val statuses = buildList {
        addAll(state.extensionStatus.values)
        if (state.compacting) add("COMPACTING")
        if (state.queued > 0) add("QUEUED ${state.queued}")
    }
    val statusText = UsageSummary.text(
        model = state.currentModel?.name,
        thinking = state.thinkingLevel,
        stats = state.stats,
        statuses = statuses,
    ).ifEmpty { "MODEL · THINKING · CONTEXT" }
    val railMessage = state.error ?: state.notice
    val railSignal = state.error != null || state.noticeTone != NoticeTone.Info
    val showStrip = screenState == ScreenCaptureState.Ready ||
        screenState == ScreenCaptureState.Blocked ||
        screenState == ScreenCaptureState.Off

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelldyeColors.Black)
            .padding(horizontal = HelldyeDimens.ScreenPadding, vertical = HelldyeDimens.Sm),
    ) {
        ExtensionWidgets(state.extensionWidgets, above = true)

        if (showStrip) {
            ScreenshotStrip(
                preview = if (screenState == ScreenCaptureState.Ready) screenshot else null,
                state = screenState,
                detail = screenshot?.let {
                    if (screenState == ScreenCaptureState.Ready) {
                        "${it.width} × ${it.height}"
                    } else {
                        null
                    }
                },
                onRemove = onRemoveScreenshot,
            )
            Spacer(Modifier.height(HelldyeDimens.Sm))
        }

        if (state.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm),
            ) {
                state.attachments.forEachIndexed { index, attachment ->
                    AttachmentChip(
                        label = attachment.label,
                        onRemove = { onRemoveAttachment(index) },
                    )
                }
            }
            Spacer(Modifier.height(HelldyeDimens.Sm))
        }

        if (attachmentMenu) {
            AttachmentSheet(
                onPhoto = onPhoto,
                onCamera = onCamera,
                onFile = onFile,
                onPaste = onPaste,
            )
            Spacer(Modifier.height(HelldyeDimens.Sm))
        }

        if (!attachmentMenu && showSlash && slashEntries.isNotEmpty()) {
            SlashPalette(entries = slashEntries, onSelect = onSlashSelect)
            Spacer(Modifier.height(HelldyeDimens.Sm))
        }

        if (railMessage != null) {
            Hairline(color = if (railSignal) HelldyeColors.SignalLine else HelldyeColors.LineSoft)
            StatusRail(
                message = railMessage,
                signal = railSignal,
                onDismiss = onDismissNotice,
            )
        }

        StatusLine(text = statusText, onClick = onOpenModelPicker)

        Composer(
            input = input,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            onVoice = onVoice,
            streaming = state.streaming,
            sendEnabled = input.isNotBlank() || state.attachments.isNotEmpty() ||
                screenState == ScreenCaptureState.Ready,
            onToggleAttachments = onToggleAttachments,
        )

        ExtensionWidgets(state.extensionWidgets, above = false)
    }
}

@Composable
private fun ScreenshotStrip(
    preview: Bitmap?,
    state: ScreenCaptureState,
    detail: String?,
    onRemove: () -> Unit,
) {
    val label = when (state) {
        ScreenCaptureState.Ready, ScreenCaptureState.Sent -> "TEMP"
        ScreenCaptureState.Off -> "OFF"
        ScreenCaptureState.Blocked -> "BLOCKED"
        ScreenCaptureState.None -> return
    }
    val labelColor = if (state == ScreenCaptureState.Blocked) {
        HelldyeColors.Signal
    } else {
        HelldyeColors.TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(1.dp, HelldyeColors.Line, RoundedCornerShape(2.dp))
            .padding(start = HelldyeDimens.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "Screenshot preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, HelldyeColors.Line, RoundedCornerShape(2.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, HelldyeColors.Line, RoundedCornerShape(2.dp)),
            )
        }
        Spacer(Modifier.width(HelldyeDimens.Sm))
        Text(
            text = "SCREEN",
            style = MaterialTheme.typography.labelMedium,
            color = HelldyeColors.Text,
        )
        Spacer(Modifier.width(HelldyeDimens.Sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
        )
        if (detail != null) {
            Spacer(Modifier.width(HelldyeDimens.Sm))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Remove screenshot",
                tint = HelldyeColors.TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun AttachmentSheet(
    onPhoto: () -> Unit,
    onCamera: () -> Unit,
    onFile: () -> Unit,
    onPaste: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelldyeColors.Black, RoundedCornerShape(4.dp))
            .border(1.dp, HelldyeColors.Line, RoundedCornerShape(4.dp)),
    ) {
        AttachmentSheetRow("PHOTO", onPhoto)
        Hairline(color = HelldyeColors.LineSoft)
        AttachmentSheetRow("CAMERA", onCamera)
        Hairline(color = HelldyeColors.LineSoft)
        AttachmentSheetRow("FILE", onFile)
        Hairline(color = HelldyeColors.LineSoft)
        AttachmentSheetRow("PASTE", onPaste)
    }
}

@Composable
private fun AttachmentSheetRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HelldyeDimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HelldyeColors.Text,
        )
    }
}

@Composable
private fun StatusRail(
    message: String,
    signal: Boolean,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HelldyeDimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (signal) HelldyeColors.Signal else HelldyeColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
private fun StatusLine(text: String, onClick: () -> Unit) {
    if (text.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HelldyeDimens.TouchTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = "Change model or thinking",
                onClick = onClick,
            )
            .padding(horizontal = HelldyeDimens.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
            color = HelldyeColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AttachmentChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = HelldyeDimens.TouchTarget)
            .background(HelldyeColors.Black, RoundedCornerShape(2.dp))
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
private fun SlashPalette(
    entries: List<SlashCommand>,
    onSelect: (SlashCommand) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .background(HelldyeColors.Black, RoundedCornerShape(2.dp))
            .border(1.dp, HelldyeColors.Line, RoundedCornerShape(2.dp)),
    ) {
        items(entries, key = { it.command }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelect(entry) }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = HelldyeDimens.ScreenPadding),
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
            Hairline(color = HelldyeColors.LineSoft)
        }
    }
}

@Composable
private fun SessionSheet(
    sessions: List<GatewaySession>,
    currentId: String?,
    loading: Boolean,
    onSwitch: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<GatewaySession?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    val filtered = remember(sessions, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            sessions
        } else {
            sessions.filter { session ->
                session.name.contains(needle, ignoreCase = true) ||
                    session.id.contains(needle, ignoreCase = true) ||
                    session.cwd.contains(needle, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(
                    color = HelldyeColors.Black,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                )
                .border(
                    width = 1.dp,
                    color = HelldyeColors.Line,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = HelldyeDimens.ScreenPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HelldyeDimens.TopBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            Hairline(color = HelldyeColors.LineSoft)
            SearchField(value = query, onValueChange = { query = it })
            Hairline(color = HelldyeColors.LineSoft)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onNew)
                    .padding(horizontal = HelldyeDimens.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NEW SESSION",
                    style = MaterialTheme.typography.labelMedium,
                    color = HelldyeColors.Text,
                )
            }
            Hairline(color = HelldyeColors.LineSoft)

            when {
                loading && sessions.isEmpty() -> Text(
                    text = "LOADING",
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                    modifier = Modifier.padding(vertical = HelldyeDimens.Lg),
                )
                filtered.isEmpty() -> Text(
                    text = "No sessions match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HelldyeColors.TextMuted,
                    modifier = Modifier.padding(vertical = HelldyeDimens.Lg),
                )
                else -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { session ->
                        val current = session.id == currentId
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .drawBehind {
                                        if (current) {
                                            drawRect(
                                                color = HelldyeColors.White,
                                                size = Size(2.dp.toPx(), size.height),
                                            )
                                        }
                                    }
                                    .clickable { onSwitch(session.id) }
                                    .padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = session.name.ifBlank { "Session ${session.id.take(8)}" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = HelldyeColors.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${session.status.uppercase(Locale.ROOT)} · ${session.id.take(8)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HelldyeColors.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        actionsFor = if (actionsFor == session.id) null else session.id
                                    },
                                    modifier = Modifier.size(HelldyeDimens.TouchTarget),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_more),
                                        contentDescription = "Session actions",
                                        tint = HelldyeColors.TextMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Hairline(color = HelldyeColors.LineSoft)
                            if (actionsFor == session.id) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = HelldyeDimens.Xs),
                                ) {
                                    TextButton(
                                        onClick = {
                                            actionsFor = null
                                            renaming = session
                                        },
                                        modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                                    ) {
                                        Text(
                                            text = "RENAME",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HelldyeColors.TextSecondary,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            actionsFor = null
                                            removing = session.id
                                        },
                                        modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                                    ) {
                                        Text(
                                            text = "REMOVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HelldyeColors.Signal,
                                        )
                                    }
                                }
                            }
                            if (removing == session.id) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = HelldyeDimens.Xs),
                                ) {
                                    Text(
                                        text = "Removes it from this list. The Pi transcript stays on the host.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HelldyeColors.TextSecondary,
                                    )
                                    Row {
                                        TextButton(
                                            onClick = { removing = null },
                                            modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                                        ) {
                                            Text(
                                                text = "KEEP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HelldyeColors.TextSecondary,
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                removing = null
                                                onDelete(session.id)
                                            },
                                            modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                                        ) {
                                            Text(
                                                text = "REMOVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HelldyeColors.Signal,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { session ->
        RenameSessionDialog(
            session = session,
            onCancel = { renaming = null },
            onSave = { name ->
                renaming = null
                onRename(session.id, name)
            },
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HelldyeDimens.Sm)
            .heightIn(min = HelldyeDimens.TouchTarget)
            .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
            .padding(horizontal = HelldyeDimens.Md, vertical = HelldyeDimens.Md),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = HelldyeColors.Text),
        singleLine = true,
        cursorBrush = SolidColor(HelldyeColors.White),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = "Search sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HelldyeColors.TextMuted,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun RenameSessionDialog(
    session: GatewaySession,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(session.id) { mutableStateOf(session.name) }
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
                .background(HelldyeColors.Black, RoundedCornerShape(4.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(4.dp))
                .padding(HelldyeDimens.Lg),
        ) {
            SectionLabel("RENAME SESSION")
            Spacer(Modifier.height(HelldyeDimens.Sm))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HelldyeDimens.TouchTarget)
                    .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp))
                    .padding(HelldyeDimens.Md),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = HelldyeColors.Text),
                cursorBrush = SolidColor(HelldyeColors.White),
                singleLine = true,
            )
            Spacer(Modifier.height(HelldyeDimens.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm)) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                ) {
                    Text(
                        text = "CANCEL",
                        style = MaterialTheme.typography.labelMedium,
                        color = HelldyeColors.TextSecondary,
                    )
                }
                TextButton(
                    onClick = { onSave(name) },
                    modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
                ) {
                    Text(
                        text = "SAVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = HelldyeColors.Text,
                    )
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
    thinking: String?,
    onOpenThinking: () -> Unit,
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
                .background(HelldyeColors.Black, RoundedCornerShape(4.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(4.dp))
                .padding(horizontal = HelldyeDimens.ScreenPadding, vertical = HelldyeDimens.Sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HelldyeDimens.TopBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            Hairline(color = HelldyeColors.LineSoft)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onOpenThinking)
                    .padding(horizontal = HelldyeDimens.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "THINKING",
                    style = MaterialTheme.typography.labelMedium,
                    color = HelldyeColors.TextMuted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = thinking?.uppercase(Locale.ROOT) ?: "DEFAULT",
                    style = MaterialTheme.typography.labelMedium,
                    color = HelldyeColors.TextSecondary,
                )
            }
            Hairline(color = HelldyeColors.LineSoft)

            when {
                loading && models.isEmpty() -> Text(
                    text = "LOADING",
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                    modifier = Modifier.padding(vertical = HelldyeDimens.Lg),
                )
                models.isEmpty() -> WarningBanner(
                    text = error ?: "No models are available on this gateway.",
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(models, key = { "${it.provider}/${it.id}" }) { model ->
                        val active = model.provider == current?.provider && model.id == current.id
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .drawBehind {
                                        if (active) {
                                            drawRect(
                                                color = HelldyeColors.White,
                                                size = Size(2.dp.toPx(), size.height),
                                            )
                                        }
                                    }
                                    .clickable { onSelect(model) }
                                    .padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = HelldyeColors.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
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
                                if (active) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HelldyeColors.TextSecondary,
                                    )
                                }
                            }
                            Hairline(color = HelldyeColors.LineSoft)
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
                .background(HelldyeColors.Black, RoundedCornerShape(4.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(4.dp))
                .padding(horizontal = HelldyeDimens.ScreenPadding, vertical = HelldyeDimens.Sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HelldyeDimens.TopBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            Hairline(color = HelldyeColors.LineSoft)

            when {
                loading && levels.isEmpty() -> Text(
                    text = "LOADING",
                    style = MaterialTheme.typography.labelSmall,
                    color = HelldyeColors.TextMuted,
                    modifier = Modifier.padding(vertical = HelldyeDimens.Lg),
                )
                levels.isEmpty() -> Text(
                    text = "No thinking levels for this model.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HelldyeColors.TextMuted,
                    modifier = Modifier.padding(vertical = HelldyeDimens.Lg),
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(levels, key = { it }) { level ->
                        val active = level == current
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .drawBehind {
                                        if (active) {
                                            drawRect(
                                                color = HelldyeColors.White,
                                                size = Size(2.dp.toPx(), size.height),
                                            )
                                        }
                                    }
                                    .clickable { onSelect(level) }
                                    .padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = level.uppercase(Locale.ROOT),
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
                            Hairline(color = HelldyeColors.LineSoft)
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
                .background(HelldyeColors.Black, RoundedCornerShape(4.dp))
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(4.dp))
                .padding(HelldyeDimens.Lg),
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
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = HelldyeDimens.Sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = HelldyeColors.Text,
                                )
                            }
                            Hairline(color = HelldyeColors.LineSoft)
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
            .heightIn(min = HelldyeDimens.TouchTarget)
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
            screenState = ScreenCaptureState.None,
            state = AssistantUiState(
                messages = listOf(
                    ChatMessage("u", ChatRole.User, "What changed in my battery settings?"),
                    ChatMessage(
                        "a",
                        ChatRole.Assistant,
                        "Battery usage is down 8% since this morning.",
                        thinking = "Checked the last 24 hours.\nCompared app drains.\nNo screenshot was sent.",
                    ),
                    ChatMessage("t1", ChatRole.Tool, "bash", tool = ToolInfo("bash", result = "done")),
                    ChatMessage("t2", ChatRole.Tool, "read", tool = ToolInfo("read", result = "ok")),
                ),
            ),
            onSubmit = {},
            onAddAttachment = {},
            onRemoveAttachment = {},
            onRemoveScreenshot = {},
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
            onLoadOlder = {},
            onExpand = {},
            onClose = {},
        )
    }
}
