package dev.scdeal.helldye.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.scdeal.helldye.R
import dev.scdeal.helldye.assistant.AssistantPhase
import dev.scdeal.helldye.assistant.AssistantUiState
import dev.scdeal.helldye.ui.components.Hairline
import dev.scdeal.helldye.ui.components.SectionLabel
import dev.scdeal.helldye.ui.components.StatusChip
import dev.scdeal.helldye.ui.components.StatusTone
import dev.scdeal.helldye.ui.components.WarningBanner
import dev.scdeal.helldye.ui.components.Wordmark
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens
import dev.scdeal.helldye.ui.theme.HelldyeTheme

@Composable
fun AssistantScreen(
    screenText: String?,
    state: AssistantUiState,
    onSubmit: (String, String?) -> Unit,
    onMic: () -> Unit,
    onDismissNotice: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler(onBack = onClose)

    LaunchedEffect(typing) {
        if (typing) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        onSubmit(text, screenText)
        input = ""
        typing = false
        keyboard?.hide()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HelldyeColors.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AssistantTopBar(contextEnabled = screenText != null)
        Hairline()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HelldyeDimens.ScreenPadding),
        ) {
            Spacer(Modifier.height(HelldyeDimens.Xl))
            Text(
                text = state.phase.status,
                style = MaterialTheme.typography.displayLarge,
                color = HelldyeColors.Text,
            )
            Spacer(Modifier.height(HelldyeDimens.Sm))
            Text(
                text = state.phase.hint,
                style = MaterialTheme.typography.bodyMedium,
                color = HelldyeColors.TextSecondary,
            )

            if (state.phase == AssistantPhase.Thinking) {
                Spacer(Modifier.height(HelldyeDimens.Lg))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = HelldyeColors.Text,
                    trackColor = HelldyeColors.Line,
                )
            }

            Spacer(Modifier.height(HelldyeDimens.Xl))
            SectionLabel("TRANSCRIPT")
            Spacer(Modifier.height(HelldyeDimens.Xs))
            Text(
                text = state.transcript.ifBlank { "No request yet." },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.transcript.isBlank()) HelldyeColors.TextMuted else HelldyeColors.Text,
            )

            Spacer(Modifier.height(HelldyeDimens.Lg))
            if (screenText != null) {
                ScreenContextPanel(screenText)
            } else {
                WarningBanner(
                    text = "Screen context is off. Enable it in system settings.",
                    action = "OPEN",
                    onAction = onOpenSettings,
                )
            }

            if (state.answer.isNotBlank()) {
                Spacer(Modifier.height(HelldyeDimens.Lg))
                SectionLabel("ANSWER")
                Spacer(Modifier.height(HelldyeDimens.Xs))
                Text(
                    text = state.answer,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HelldyeColors.Text,
                )
            }

            state.notice?.let { notice ->
                Spacer(Modifier.height(HelldyeDimens.Lg))
                WarningBanner(
                    text = notice,
                    action = "DISMISS",
                    onAction = onDismissNotice,
                )
            }

            Spacer(Modifier.height(HelldyeDimens.Xl))
            Hairline(color = HelldyeColors.LineSoft)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = HelldyeDimens.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("LOCAL SESSION", style = MaterialTheme.typography.labelSmall, color = HelldyeColors.TextMuted)
                Text("TEXT ONLY · NO SCREENSHOT", style = MaterialTheme.typography.labelSmall, color = HelldyeColors.TextMuted)
            }
        }

        if (typing) {
            Composer(
                input = input,
                onInputChange = { input = it },
                onSend = ::send,
                focusRequester = focusRequester,
            )
        }

        AssistantBottomBar(
            typing = typing,
            onMic = onMic,
            onType = {
                typing = !typing
                if (!typing) keyboard?.hide()
            },
            onClose = onClose,
        )
    }
}

@Composable
private fun AssistantTopBar(contextEnabled: Boolean) {
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
            label = if (contextEnabled) "SCREEN ON" else "SCREEN OFF",
            tone = if (contextEnabled) StatusTone.Active else StatusTone.Neutral,
        )
    }
}

@Composable
private fun ScreenContextPanel(text: String) {
    val lineCount = text.lineSequence().count { it.isNotBlank() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HelldyeColors.Line, RoundedCornerShape(0.dp))
            .padding(HelldyeDimens.Md),
    ) {
        SectionLabel("SCREEN CONTEXT")
        Spacer(Modifier.height(HelldyeDimens.Xs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(HelldyeDimens.Sm))
        Text(
            text = "$lineCount LINES · ${text.length} CHARS · TEXT ONLY",
            style = MaterialTheme.typography.labelSmall,
            color = HelldyeColors.TextMuted,
        )
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HelldyeDimens.ScreenPadding, vertical = HelldyeDimens.Sm)
            .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
            .padding(start = HelldyeDimens.Md, end = HelldyeDimens.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = HelldyeColors.Text),
            cursorBrush = SolidColor(HelldyeColors.White),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (input.isEmpty()) {
                        Text(
                            text = "Type a request",
                            style = MaterialTheme.typography.bodyLarge,
                            color = HelldyeColors.TextMuted,
                        )
                    }
                    inner()
                }
            },
        )
        TextButton(
            onClick = onSend,
            modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            Text(
                text = "SEND",
                style = MaterialTheme.typography.labelMedium,
                color = HelldyeColors.Text,
            )
        }
    }
}

@Composable
private fun AssistantBottomBar(
    typing: Boolean,
    onMic: () -> Unit,
    onType: () -> Unit,
    onClose: () -> Unit,
) {
    Hairline()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = HelldyeDimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMic,
            modifier = Modifier
                .size(HelldyeDimens.TouchTarget)
                .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Microphone",
                tint = HelldyeColors.Text,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onType,
            modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_keyboard),
                contentDescription = null,
                tint = if (typing) HelldyeColors.Text else HelldyeColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "TYPE",
                style = MaterialTheme.typography.labelMedium,
                color = if (typing) HelldyeColors.Text else HelldyeColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onClose,
            modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = null,
                tint = HelldyeColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "CLOSE",
                style = MaterialTheme.typography.labelMedium,
                color = HelldyeColors.TextSecondary,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AssistantScreenPreview() {
    HelldyeTheme {
        AssistantScreen(
            screenText = "Settings / Battery / Battery usage. The screen shows the last 24 hours.",
            state = AssistantUiState(
                phase = AssistantPhase.Answer,
                transcript = "What changed in my battery settings?",
                answer = "Battery usage is down 8% since this morning. The Pi gateway is the next integration point.",
            ),
            onSubmit = { _, _ -> },
            onMic = {},
            onDismissNotice = {},
            onOpenSettings = {},
            onClose = {},
        )
    }
}
