package dev.scdeal.helldye.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    screenText: String?,
    assistReceived: Boolean,
    state: AssistantUiState,
    onSubmit: (String, String?) -> Unit,
    onAddAttachment: (Attachment) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onNotice: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var attachmentMenu by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun send() {
        if (input.isBlank() && state.attachments.isEmpty()) return
        onSubmit(input, screenText)
        input = ""
        attachmentMenu = false
        keyboard?.hide()
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
        if (expanded) expanded = false else onClose()
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (expanded) HelldyeColors.Black else Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AssistantTopBar(
                contextEnabled = screenText != null,
                assistReceived = assistReceived,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
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
                        EmptyState(
                            hasScreen = screenText != null,
                            hasAssist = assistReceived,
                            expanded = expanded,
                        )
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
                    .background(if (expanded) HelldyeColors.Black else HelldyeColors.Black.copy(alpha = 0.62f))
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
                            .padding(bottom = HelldyeDimens.Sm),
                        horizontalArrangement = Arrangement.spacedBy(HelldyeDimens.Sm),
                    ) {
                        AttachmentAction("PHOTO") { attachmentMenu = false; photoPicker.launch("image/*") }
                        AttachmentAction("SCREEN") {
                            attachmentMenu = false
                            if (screenText.isNullOrBlank()) {
                                onNotice("No screen text is available in this request.")
                            } else if (state.attachments.any { it is Attachment.Screen }) {
                                onNotice("Screen context is already attached.")
                            } else {
                                onAddAttachment(Attachment.Screen(screenText))
                            }
                        }
                        AttachmentAction("FILE") { attachmentMenu = false; filePicker.launch("*/*") }
                        AttachmentAction("SETTINGS", onOpenSettings)
                    }
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
    }
}

@Composable
private fun AssistantTopBar(
    contextEnabled: Boolean,
    assistReceived: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClose: () -> Unit,
) {
    val label = when {
        contextEnabled -> "SCREEN ON"
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
            tone = if (contextEnabled) StatusTone.Active else StatusTone.Neutral,
        )
        IconButton(
            onClick = onToggleExpanded,
            modifier = Modifier.size(HelldyeDimens.TouchTarget),
        ) {
            Icon(
                painter = painterResource(if (expanded) R.drawable.ic_collapse else R.drawable.ic_expand),
                contentDescription = if (expanded) "Collapse assistant" else "Open full session",
                tint = HelldyeColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
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
private fun EmptyState(hasScreen: Boolean, hasAssist: Boolean, expanded: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (expanded) 64.dp else 36.dp)
            .background(
                color = if (expanded) Color.Transparent else HelldyeColors.Black.copy(alpha = 0.42f),
                shape = RoundedCornerShape(2.dp),
            )
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
            text = when {
                hasScreen -> "Screen text is attached to your next request."
                hasAssist -> "Assist is open, but the screen did not provide text."
                else -> "Open HELLDYE from the assist gesture to attach screen text."
            },
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
            color = if (message.role == ChatRole.Tool) HelldyeColors.TextMuted else HelldyeColors.TextMuted,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (message.role == ChatRole.Assistant) {
                        HelldyeColors.Black.copy(alpha = 0.30f)
                    } else {
                        HelldyeColors.Black.copy(alpha = 0.55f)
                    },
                    shape = RoundedCornerShape(2.dp),
                )
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
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    onToggleAttachments: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelldyeColors.Black.copy(alpha = 0.90f), shape)
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
            screenText = "Settings / Battery / Battery usage. The screen shows the last 24 hours.",
            assistReceived = true,
            state = AssistantUiState(
                messages = listOf(
                    ChatMessage("u", ChatRole.User, "What changed in my battery settings?"),
                    ChatMessage("a", ChatRole.Assistant, "Battery usage is down 8% since this morning."),
                    ChatMessage("t", ChatRole.Tool, "bash DONE"),
                ),
            ),
            onSubmit = { _, _ -> },
            onAddAttachment = {},
            onRemoveAttachment = {},
            onNotice = {},
            onDismissNotice = {},
            onOpenSettings = {},
            onClose = {},
        )
    }
}
