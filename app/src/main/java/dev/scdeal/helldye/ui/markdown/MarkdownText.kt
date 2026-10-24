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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.scdeal.helldye.ui.theme.HelldyeColors
import dev.scdeal.helldye.ui.theme.HelldyeDimens

private val inlineToken = Regex(
    "(\\*\\*\\*.+?\\*\\*\\*" +
        "|\\*\\*.+?\\*\\*" +
        "|~~.+?~~" +
        "|`[^`]+`" +
        "|\\[([^]]+)]\\(([^)\\s]+)\\)" +
        "|\\*[^*\\n]+\\*" +
        "|(?<![A-Za-z0-9])_[^_\\n]+_(?![A-Za-z0-9]))",
)
private val codeShape = RoundedCornerShape(2.dp)

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(HelldyeDimens.Sm))
            when (block) {
                is MarkdownBlock.Paragraph -> InlineText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                is MarkdownBlock.Heading -> InlineText(
                    text = block.text,
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                )
                is MarkdownBlock.Code -> CodeBlock(block.language, block.code)
                is MarkdownBlock.Bullet -> Column {
                    block.items.forEach { item ->
                        Row {
                            Text("•", style = MaterialTheme.typography.bodyLarge, color = HelldyeColors.TextMuted)
                            Spacer(Modifier.width(HelldyeDimens.Sm))
                            InlineText(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge,
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
                            InlineText(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is MarkdownBlock.Todo -> Column {
                    block.items.forEach { item ->
                        Row {
                            Text(
                                text = if (item.checked) "[x]" else "[ ]",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = HelldyeColors.TextMuted,
                            )
                            Spacer(Modifier.width(HelldyeDimens.Sm))
                            InlineText(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = if (item.checked) {
                                        TextDecoration.LineThrough
                                    } else {
                                        null
                                    },
                                ),
                                color = if (item.checked) {
                                    HelldyeColors.TextMuted
                                } else {
                                    HelldyeColors.Text
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is MarkdownBlock.Table -> TableBlock(block.header, block.rows)
                is MarkdownBlock.Quote -> InlineText(
                    text = block.text,
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
private fun InlineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = HelldyeColors.Text,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val annotated = remember(text) { inline(text) }
    Text(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
private fun TableBlock(header: List<String>, rows: List<List<String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        TableRow(header, isHeader = true)
        rows.forEach { row ->
            TableRow(row, isHeader = false)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, isHeader: Boolean) {
    Row(
        modifier = Modifier.background(
            if (isHeader) HelldyeColors.SurfaceRaised else HelldyeColors.Surface,
        ),
    ) {
        cells.forEach { cell ->
            InlineText(
                text = cell,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isHeader) HelldyeColors.Text else HelldyeColors.TextSecondary,
                modifier = Modifier
                    .width(128.dp)
                    .border(0.5.dp, HelldyeColors.LineSoft)
                    .padding(6.dp),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
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
    appendInline(text)
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    var last = 0
    for (match in inlineToken.findAll(text)) {
        append(text.substring(last, match.range.first))
        val token = match.value
        when {
            token.startsWith("***") -> withStyle(
                SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic),
            ) {
                appendInline(token.substring(3, token.length - 3))
            }
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInline(token.substring(2, token.length - 2))
            }
            token.startsWith("~~") -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInline(token.substring(2, token.length - 2))
            }
            token.startsWith("`") -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = HelldyeColors.SurfaceRaised,
                ),
            ) {
                append(token.substring(1, token.length - 1))
            }
            token.startsWith("[") -> {
                val label = match.groupValues[2]
                val url = match.groupValues[3]
                if (url.isBlank()) {
                    append(token)
                } else {
                    withLink(LinkAnnotation.Url(url)) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            appendInline(label.ifBlank { url })
                        }
                    }
                }
            }
            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInline(token.substring(1, token.length - 1))
            }
            token.startsWith("_") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInline(token.substring(1, token.length - 1))
            }
            else -> append(token)
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
