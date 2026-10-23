package dev.scdeal.helldye.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.scdeal.helldye.ui.components.Hairline
import dev.scdeal.helldye.ui.components.HelldyeButton
import dev.scdeal.helldye.ui.components.HelldyeButtonStyle
import dev.scdeal.helldye.ui.components.SectionLabel
import dev.scdeal.helldye.ui.components.SettingRow
import dev.scdeal.helldye.ui.components.WarningBanner
import dev.scdeal.helldye.ui.components.Wordmark
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens
import dev.scdeal.helldye.ui.theme.HelldyeTheme

@Composable
fun OnboardingScreen(
    roleAvailable: Boolean,
    roleHeld: Boolean,
    onRequestRole: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenAssistant: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HelldyeColors.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HelldyeDimens.ScreenPadding),
    ) {
        Spacer(Modifier.height(HelldyeDimens.Xl))
        Wordmark()

        Spacer(Modifier.height(40.dp))
        SectionLabel("01 / SETUP")
        Spacer(Modifier.height(HelldyeDimens.Sm))
        Text(
            text = "Your assistant.\nOn your terms.",
            style = MaterialTheme.typography.headlineLarge,
            color = HelldyeColors.Text,
        )
        Spacer(Modifier.height(HelldyeDimens.Md))
        Text(
            text = "Screen context is read only when you ask. Nothing leaves the device unless you choose a backend.",
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
        )

        Spacer(Modifier.height(HelldyeDimens.Xl))
        Hairline()

        if (!roleAvailable) {
            Spacer(Modifier.height(HelldyeDimens.Md))
            WarningBanner(
                text = "This device does not allow third-party assistants.",
                action = "SETTINGS",
                onAction = onOpenAssistantSettings,
            )
        }

        SettingRow(
            name = "Default assistant",
            state = when {
                !roleAvailable -> "UNAVAILABLE"
                roleHeld -> "HELD"
                else -> "NOT SET"
            },
            action = if (roleAvailable) {
                if (roleHeld) "CHANGE" else "SET"
            } else {
                null
            },
            onAction = if (roleHeld) onOpenAssistantSettings else onRequestRole,
            modifier = Modifier.padding(vertical = HelldyeDimens.Xs),
        )
        Hairline(color = HelldyeColors.LineSoft)
        SettingRow(
            name = "Screen context",
            state = "SYSTEM CONTROLLED",
            action = "OPEN",
            onAction = onOpenAssistantSettings,
            modifier = Modifier.padding(vertical = HelldyeDimens.Xs),
        )
        Hairline(color = HelldyeColors.LineSoft)
        SettingRow(
            name = "Microphone",
            state = "NOT CONNECTED",
            modifier = Modifier.padding(vertical = HelldyeDimens.Xs),
        )
        Hairline(color = HelldyeColors.LineSoft)

        Spacer(Modifier.height(32.dp))

        when {
            !roleAvailable -> Unit
            roleHeld -> HelldyeButton(
                text = "OPEN ASSISTANT",
                onClick = onOpenAssistant,
                modifier = Modifier.fillMaxWidth(),
                style = HelldyeButtonStyle.Primary,
            )

            else -> HelldyeButton(
                text = "SET AS DEFAULT ASSISTANT",
                onClick = onRequestRole,
                modifier = Modifier.fillMaxWidth(),
                style = HelldyeButtonStyle.Primary,
            )
        }

        Spacer(Modifier.height(HelldyeDimens.Sm))
        HelldyeButton(
            text = "OPEN SYSTEM SETTINGS",
            onClick = onOpenAssistantSettings,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(HelldyeDimens.Xl))
        Text(
            text = "The power-button gesture is controlled by the system. Use the assist gesture if your device supports one.",
            style = MaterialTheme.typography.labelSmall,
            color = HelldyeColors.TextMuted,
        )
        Spacer(Modifier.height(HelldyeDimens.Xl))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun OnboardingPreview() {
    HelldyeTheme {
        OnboardingScreen(
            roleAvailable = true,
            roleHeld = false,
            onRequestRole = {},
            onOpenAssistantSettings = {},
            onOpenAssistant = {},
        )
    }
}
