package dev.scdeal.helldye.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.scdeal.helldye.BuildConfig
import dev.scdeal.helldye.R
import dev.scdeal.helldye.gateway.GatewayClient
import dev.scdeal.helldye.gateway.GatewayConfig
import dev.scdeal.helldye.gateway.GatewaySessionStore
import dev.scdeal.helldye.gateway.GatewayUrlPolicy
import dev.scdeal.helldye.gateway.SettingsStore
import dev.scdeal.helldye.ui.components.Hairline
import dev.scdeal.helldye.ui.components.HelldyeButton
import dev.scdeal.helldye.ui.components.HelldyeButtonStyle
import dev.scdeal.helldye.ui.components.SectionLabel
import dev.scdeal.helldye.ui.components.SettingRow
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens
import dev.scdeal.helldye.ui.theme.HelldyeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val store = SettingsStore(this)
        setContent {
            HelldyeTheme {
                var config by remember { mutableStateOf(store.load()) }
                var message by remember { mutableStateOf<String?>(null) }
                var success by remember { mutableStateOf(true) }
                var readAnswers by remember { mutableStateOf(store.readAnswersEnabled()) }
                val scope = rememberCoroutineScope()

                SettingsScreen(
                    config = config,
                    message = message,
                    success = success,
                    readAnswers = readAnswers,
                    onToggleReadAnswers = {
                        readAnswers = !readAnswers
                        store.setReadAnswers(readAnswers)
                    },
                    onConfigChange = {
                        config = it
                        message = null
                    },
                    onSave = {
                        val error = GatewayUrlPolicy.validate(config.url, BuildConfig.DEBUG)
                        if (error != null) {
                            success = false
                            message = error
                        } else {
                            success = store.save(config)
                            GatewaySessionStore.reset()
                            message = if (success) "SAVED" else "TOKEN STORAGE UNAVAILABLE"
                        }
                    },
                    onTest = {
                        val error = GatewayUrlPolicy.validate(config.url, BuildConfig.DEBUG)
                        if (error != null) {
                            success = false
                            message = error
                        } else {
                            scope.launch {
                                success = true
                                message = "CONNECTING"
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { GatewayClient(config).info() }
                                }
                                result.onSuccess {
                                    success = true
                                    message = "CONNECTED · ${it.optString("version", "gateway")}"
                                }.onFailure {
                                    success = false
                                    message = it.message ?: "CONNECTION FAILED"
                                }
                            }
                        }
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    config: GatewayConfig,
    message: String?,
    success: Boolean,
    readAnswers: Boolean,
    onToggleReadAnswers: () -> Unit,
    onConfigChange: (GatewayConfig) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HelldyeColors.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HelldyeDimens.ScreenPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HelldyeDimens.TopBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = HelldyeDimens.TouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = HelldyeColors.TextSecondary,
                )
            }
            Spacer(Modifier.width(HelldyeDimens.Sm))
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.labelLarge,
                color = HelldyeColors.Text,
            )
        }
        Hairline(color = HelldyeColors.LineSoft)

        Spacer(Modifier.height(HelldyeDimens.Xl))
        SectionLabel("GATEWAY")
        Spacer(Modifier.height(HelldyeDimens.Sm))
        Text(
            text = "HELLDYE talks directly to a gateway on your Pi host. The gateway owns the Pi-Bolt process and keeps sessions alive when the phone disconnects.",
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
        )
        Spacer(Modifier.height(HelldyeDimens.Xl))

        ConfigField(
            label = "GATEWAY URL",
            value = config.url,
            placeholder = "https://phone.tailnet.ts.net",
            keyboardType = KeyboardType.Uri,
            onValueChange = { onConfigChange(config.copy(url = it)) },
        )
        Spacer(Modifier.height(HelldyeDimens.Lg))
        ConfigField(
            label = "TOKEN",
            value = config.token,
            placeholder = "required bearer token",
            keyboardType = KeyboardType.Password,
            password = true,
            onValueChange = { onConfigChange(config.copy(token = it)) },
        )
        Spacer(Modifier.height(HelldyeDimens.Lg))
        ConfigField(
            label = "WORKSPACE",
            value = config.cwd,
            placeholder = "optional, e.g. /home/you/project",
            keyboardType = KeyboardType.Text,
            onValueChange = { onConfigChange(config.copy(cwd = it)) },
        )

        Spacer(Modifier.height(HelldyeDimens.Xl))
        HelldyeButton(
            text = "SAVE",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            style = HelldyeButtonStyle.Primary,
        )
        Spacer(Modifier.height(HelldyeDimens.Sm))
        HelldyeButton(
            text = "TEST CONNECTION",
            onClick = onTest,
            modifier = Modifier.fillMaxWidth(),
        )

        if (message != null) {
            Spacer(Modifier.height(HelldyeDimens.Md))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = if (success) HelldyeColors.TextSecondary else HelldyeColors.Signal,
            )
        }

        Spacer(Modifier.height(HelldyeDimens.Xl))
        Hairline(color = HelldyeColors.LineSoft)
        Spacer(Modifier.height(HelldyeDimens.Md))
        SectionLabel("PRIVACY")
        SettingRow(name = "Screen context", state = "SCREENSHOT ONLY")
        Hairline(color = HelldyeColors.LineSoft)
        SettingRow(name = "Screenshots", state = "MEMORY ONLY")

        Spacer(Modifier.height(HelldyeDimens.Xl))
        Hairline(color = HelldyeColors.LineSoft)
        Spacer(Modifier.height(HelldyeDimens.Md))
        SectionLabel("SPEECH")
        SettingRow(name = "Read answers", state = if (readAnswers) "ON" else "OFF")
        Spacer(Modifier.height(HelldyeDimens.Sm))
        Text(
            text = "Off by default. Reads the final answer only, skips code blocks, and stops when you leave the session.",
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextSecondary,
        )
        Spacer(Modifier.height(HelldyeDimens.Sm))
        HelldyeButton(
            text = if (readAnswers) "TURN READ-ALOUD OFF" else "TURN READ-ALOUD ON",
            onClick = onToggleReadAnswers,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(HelldyeDimens.Xl))
        Hairline(color = HelldyeColors.LineSoft)
        Spacer(Modifier.height(HelldyeDimens.Md))
        SectionLabel("ABOUT")
        SettingRow(name = "Version", state = BuildConfig.VERSION_NAME)

        Spacer(Modifier.height(HelldyeDimens.Xl))
        Hairline(color = HelldyeColors.LineSoft)
        Spacer(Modifier.height(HelldyeDimens.Md))
        Text(
            text = "Run the gateway on the same machine as Pi-Bolt. Over Tailscale, bind the gateway to localhost and use tailscale serve; do not expose it to the public internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.TextMuted,
        )
        Spacer(Modifier.height(HelldyeDimens.Xl))
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    password: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Column {
        SectionLabel(label)
        Spacer(Modifier.height(HelldyeDimens.Sm))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = HelldyeColors.Text),
            singleLine = true,
            cursorBrush = SolidColor(HelldyeColors.White),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HelldyeDimens.TouchTarget)
                        .border(1.dp, HelldyeColors.LineStrong, RoundedCornerShape(2.dp))
                        .padding(horizontal = HelldyeDimens.Md, vertical = HelldyeDimens.Md),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HelldyeColors.TextMuted,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SettingsPreview() {
    HelldyeTheme {
        SettingsScreen(
            config = GatewayConfig(url = "https://phone.tailnet.ts.net", cwd = "/home/you/code"),
            message = "CONNECTED · pi-bolt",
            success = true,
            readAnswers = false,
            onToggleReadAnswers = {},
            onConfigChange = {},
            onSave = {},
            onTest = {},
            onBack = {},
        )
    }
}
