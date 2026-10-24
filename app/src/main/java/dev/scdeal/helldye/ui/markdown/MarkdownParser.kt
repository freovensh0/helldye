package dev.scdeal.helldye.ui.markdown

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock

    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Code(val language: String, val code: String) : MarkdownBlock

    data class Bullet(val items: List<String>) : MarkdownBlock

    data class Numbered(val items: List<String>) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data object Divider : MarkdownBlock
}

/** Minimal block parser for assistant output: fenced code, headings, lists, quotes, rules. */
object MarkdownParser {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^[-*+]\\s+(.*)$")
    private val numbered = Regex("^\\d+[.)]\\s+(.*)$")
    private val divider = Regex("^(-{3,}|\\*{3,}|_{3,})$")

    fun parse(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = text.lines()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code.appendLine(lines[index])
                    index++
                }
                if (index < lines.size) index++
                blocks += MarkdownBlock.Code(language, code.toString().trimEnd('\n'))
                continue
            }

            if (trimmed.isEmpty()) {
                index++
                continue
            }

            val headingMatch = heading.matchEntire(trimmed)
            if (headingMatch != null) {
                blocks += MarkdownBlock.Heading(
                    headingMatch.groupValues[1].length,
                    headingMatch.groupValues[2],
                )
                index++
                continue
            }

            if (divider.matches(trimmed)) {
                blocks += MarkdownBlock.Divider
                index++
                continue
            }

            if (bullet.matches(trimmed)) {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val item = bullet.matchEntire(lines[index].trim()) ?: break
                    items += item.groupValues[1]
                    index++
                }
                blocks += MarkdownBlock.Bullet(items)
                continue
            }

            if (numbered.matches(trimmed)) {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val item = numbered.matchEntire(lines[index].trim()) ?: break
                    items += item.groupValues[1]
                    index++
                }
                blocks += MarkdownBlock.Numbered(items)
                continue
            }

            if (trimmed.startsWith(">")) {
                val quoted = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quoted += lines[index].trim().removePrefix(">").trimStart()
                    index++
                }
                blocks += MarkdownBlock.Quote(quoted.joinToString("\n"))
                continue
            }

            val paragraph = mutableListOf<String>()
            while (index < lines.size) {
                val current = lines[index].trim()
                if (current.isEmpty() ||
                    current.startsWith("```") ||
                    heading.matches(current) ||
                    bullet.matches(current) ||
                    numbered.matches(current) ||
                    current.startsWith(">") ||
                    divider.matches(current)
                ) {
                    break
                }
                paragraph += current
                index++
            }
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
        }
        return blocks
    }
}
