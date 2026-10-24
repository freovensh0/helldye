package dev.scdeal.helldye.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens

private val inlineToken = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
private val codeShape = RoundedCornerShape(2.dp)

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(HelldyeDimens.Sm))
            when (block) {
                is MarkdownBlock.Paragraph -> Text(
                    text = inline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = HelldyeColors.Text,
                )
                is MarkdownBlock.Heading -> Text(
                    text = inline(block.text),
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = HelldyeColors.Text,
                )
                is MarkdownBlock.Code -> CodeBlock(block.language, block.code)
                is MarkdownBlock.Bullet -> Column {
                    block.items.forEach { item ->
                        Row {
                            Text("•", style = MaterialTheme.typography.bodyLarge, color = HelldyeColors.TextMuted)
                            Spacer(Modifier.width(HelldyeDimens.Sm))
                            Text(
                                text = inline(item),
                                style = MaterialTheme.typography.bodyLarge,
                                color = HelldyeColors.Text,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is MarkdownBlock.Numbered -> Column {
                    block.items.forEachIndexed { itemIndex, item ->
                        Row {
                            Text(
                                text = "${itemIndex + 1}.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = HelldyeColors.TextMuted,
                            )
                            Spacer(Modifier.width(HelldyeDimens.Sm))
                            Text(
                                text = inline(item),
                                style = MaterialTheme.typography.bodyLarge,
                                color = HelldyeColors.Text,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is MarkdownBlock.Quote -> Text(
                    text = inline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HelldyeColors.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, HelldyeColors.LineSoft, codeShape)
                        .padding(HelldyeDimens.Sm),
                )
                MarkdownBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(HelldyeColors.LineSoft),
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelldyeColors.Surface, codeShape)
            .border(1.dp, HelldyeColors.LineSoft, codeShape),
    ) {
        if (language.isNotBlank()) {
            Text(
                text = language.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HelldyeColors.TextMuted,
                modifier = Modifier.padding(start = HelldyeDimens.Md, top = HelldyeDimens.Sm),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .horizontalScroll(rememberScrollState())
                .padding(HelldyeDimens.Md),
        ) {
            Text(
                text = code.ifBlank { " " },
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = HelldyeColors.Text,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in inlineToken.findAll(text)) {
        append(text.substring(last, match.range.first))
        val token = match.value
        if (token.startsWith("**")) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(token.removeSurrounding("**"))
            }
        } else {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = HelldyeColors.SurfaceRaised,
                ),
            ) {
                append(token.removeSurrounding("`"))
            }
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
