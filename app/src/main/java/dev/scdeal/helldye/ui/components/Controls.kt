package dev.scdeal.helldye.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scdeal.helldye.R
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens
import java.util.Locale

enum class HelldyeButtonStyle { Primary, Secondary, Danger }

@Composable
fun HelldyeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: HelldyeButtonStyle = HelldyeButtonStyle.Secondary,
    enabled: Boolean = true,
) {
    val container = if (style == HelldyeButtonStyle.Primary && enabled) {
        HelldyeColors.White
    } else {
        Color.Transparent
    }
    val content = when {
        !enabled -> HelldyeColors.TextMuted
        style == HelldyeButtonStyle.Primary -> HelldyeColors.Black
        style == HelldyeButtonStyle.Danger -> HelldyeColors.Signal
        else -> HelldyeColors.Text
    }
    val border = when {
        !enabled -> HelldyeColors.LineSoft
        style == HelldyeButtonStyle.Danger -> HelldyeColors.SignalLine
        style == HelldyeButtonStyle.Primary -> null
        else -> HelldyeColors.LineStrong
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = HelldyeDimens.TouchTarget),
        shape = RoundedCornerShape(2.dp),
        border = border?.let { BorderStroke(1.dp, it) },
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = HelldyeColors.TextMuted,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = HelldyeColors.Line,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(HelldyeDimens.Hairline)
            .background(color),
    )
}

enum class StatusTone { Neutral, Active, Danger }

@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val content = when (tone) {
        StatusTone.Neutral -> HelldyeColors.TextMuted
        StatusTone.Active -> HelldyeColors.Text
        StatusTone.Danger -> HelldyeColors.Signal
    }
    val border = if (tone == StatusTone.Danger) HelldyeColors.SignalLine else HelldyeColors.LineStrong

    Row(
        modifier = modifier
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp).background(content, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    signal: Boolean = true,
) {
    val accent = if (signal) HelldyeColors.Signal else HelldyeColors.TextMuted
    val line = if (signal) HelldyeColors.SignalLine else HelldyeColors.LineSoft
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, line, RoundedCornerShape(2.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = HelldyeColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = action.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.ROOT),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = HelldyeColors.TextMuted,
    )
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = "HELLDYE",
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = dev.scdeal.helldye.ui.theme.JetBrainsMono,
                letterSpacing = 0.9.sp,
            ),
            color = HelldyeColors.Text,
        )
        Box(
            Modifier
                .padding(top = 5.dp)
                .width(18.dp)
                .height(1.dp)
                .background(HelldyeColors.SignalLine),
        )
    }
}

@Composable
fun SettingRow(
    name: String,
    state: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = HelldyeColors.Text)
            Text(
                state.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelMedium,
                color = HelldyeColors.TextMuted,
            )
        }
        if (action != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    action.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    color = HelldyeColors.Text,
                )
            }
        }
    }
}
