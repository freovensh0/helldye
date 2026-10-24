package dev.scdeal.helldye.ui

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scdeal.helldye.R
import dev.scdeal.helldye.assistant.AssistantUiState
import dev.scdeal.helldye.assistant.Attachment
import dev.scdeal.helldye.assistant.ChatMessage
import dev.scdeal.helldye.assistant.ChatRole
import dev.scdeal.helldye.assistant.SlashCommand
import dev.scdeal.helldye.assistant.SlashCommands
import dev.scdeal.helldye.gateway.GatewayModel
import dev.scdeal.helldye.ui.components.SectionLabel
import dev.scdeal.helldye.ui.components.StatusChip
import dev.scdeal.helldye.ui.components.StatusTone
import dev.scdeal.helldye.ui.components.WarningBanner
import dev.scdeal.helldye.ui.components.Wordmark
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens
import dev.scdeal.helldye.ui.theme.HelldyeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
    onNewSession: () -> Unit,
    onAbort: () -> Unit,
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
            "/help" -> {
                input = ""
                attachmentMenu = false
                onNotice("Commands: /model, /new, /abort, /help")
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

    BackHandler {
        when {
            state.modelPickerOpen -> onCloseModelPicker()
            attachmentMenu -> attachmentMenu = false
            showSlash -> input = ""
            else -> onClose()
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (overlay) Color.Transparent else HelldyeColors.Black),
    ) {
        if (overlay) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HelldyeColors.Black.copy(alpha = 0.45f)),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AssistantTopBar(
                overlay = overlay,
                screenshot = screenshot,
                assistReceived = assistReceived,
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
                if (state.error != null || state.notice != null) {
                    WarningBanner(
                        text = state.error ?: state.notice.orEmpty(),
                        action = "DISMISS",
                        onAction = onDismissNotice,
                    )
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
                        AttachmentAction("MODEL") {
                            attachmentMenu = false
                            onOpenModelPicker()
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
                    sendEnabled = input.isNotBlank() || state.attachments.isNotEmpty(),
                    onToggleAttachments = { attachmentMenu = !attachmentMenu },
                )
            }
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
    }
}

@Composable
private fun AssistantTopBar(
    overlay: Boolean,
    screenshot: Bitmap?,
    assistReceived: Boolean,
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
            Text(
                text = message.text.ifBlank { "…" },
                style = if (message.role == ChatRole.Tool) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = if (message.role == ChatRole.Tool) HelldyeColors.TextMuted else HelldyeColors.Text,
            )
        }
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
private fun SlashPalette(
    entries: List<SlashCommand>,
    onSelect: (SlashCommand) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .background(HelldyeColors.Surface, RoundedCornerShape(2.dp))
            .border(1.dp, HelldyeColors.LineSoft, RoundedCornerShape(2.dp)),
    ) {
        entries.take(7).forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(entry) }
                    .heightIn(min = 44.dp)
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
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
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
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "Send",
                tint = if (sendEnabled) HelldyeColors.Text else HelldyeColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private suspend fun readPhoto(context: Context, uri: Uri): Attachment.Photo = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "image/jpeg"
    val bytes = resolver.openInputStream(uri)?.use { readLimited(it, 4 * 1024 * 1024) }
        ?: error("Could not open the image.")
    Attachment.Photo(
        name = displayName(context, uri) ?: "photo",
        mimeType = mime,
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

private suspend fun readScreenshot(bitmap: Bitmap): Attachment.Photo = withContext(Dispatchers.IO) {
    val maxDimension = 1600
    val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap
    }

    val output = ByteArrayOutputStream()
    var quality = 80
    var bytes: ByteArray
    do {
        output.reset()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
        bytes = output.toByteArray()
        quality -= 15
    } while (bytes.size > 4 * 1024 * 1024 && quality >= 35)

    if (scaled !== bitmap) scaled.recycle()
    Attachment.Photo(
        name = "SCREEN",
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
            onNewSession = {},
            onAbort = {},
            onExpand = {},
            onClose = {},
        )
    }
}
