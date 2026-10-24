package dev.scdeal.helldye.ui.markdown

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock

    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Code(val language: String, val code: String) : MarkdownBlock

    data class Bullet(val items: List<String>) : MarkdownBlock

    data class Numbered(val items: List<String>) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data class Todo(val items: List<TodoItem>) : MarkdownBlock

    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock

    data object Divider : MarkdownBlock
}

data class TodoItem(val text: String, val checked: Boolean)

/** Minimal block parser for assistant output: fenced code, headings, lists, quotes, rules. */
object MarkdownParser {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^[-*+]\\s+(.*)$")
    private val todoItem = Regex("^[-*+]\\s+\\[([xX ])]\\s+(.*)$")
    private val numbered = Regex("^\\d+[.)]\\s+(.*)$")
    private val divider = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val tableSeparator = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")

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

            val todoMatch = todoItem.matchEntire(trimmed)
            if (todoMatch != null) {
                val items = mutableListOf<TodoItem>()
                while (index < lines.size) {
                    val item = todoItem.matchEntire(lines[index].trim()) ?: break
                    items += TodoItem(
                        text = item.groupValues[2],
                        checked = item.groupValues[1].equals("x", ignoreCase = true),
                    )
                    index++
                }
                blocks += MarkdownBlock.Todo(items)
                continue
            }

            if (isTableStart(lines, index)) {
                val header = tableCells(trimmed)
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].trim().startsWith("|")) {
                    rows += tableCells(lines[index])
                    index++
                }
                blocks += MarkdownBlock.Table(header, rows)
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
                    todoItem.matches(current) ||
                    numbered.matches(current) ||
                    current.startsWith(">") ||
                    isTableStart(lines, index) ||
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

    private fun tableCells(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }

    private fun isTableStart(lines: List<String>, index: Int): Boolean =
        lines[index].trim().startsWith("|") &&
            index + 1 < lines.size &&
            tableSeparator.matches(lines[index + 1].trim())
}
