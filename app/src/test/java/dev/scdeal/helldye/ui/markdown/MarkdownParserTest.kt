package dev.scdeal.helldye.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `fenced code keeps its language and body`() {
        val blocks = MarkdownParser.parse(
            "Run this:\n\n```kotlin\nval x = 1\nprintln(x)\n```\n\nDone.",
        )
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("Run this:"),
                MarkdownBlock.Code("kotlin", "val x = 1\nprintln(x)"),
                MarkdownBlock.Paragraph("Done."),
            ),
            blocks,
        )
    }

    @Test
    fun `an open fence streams as code`() {
        assertEquals(
            listOf(MarkdownBlock.Code("", "val x = 1")),
            MarkdownParser.parse("```\nval x = 1"),
        )
    }

    @Test
    fun `headings lists quotes and rules are recognized`() {
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "Title"),
                MarkdownBlock.Bullet(listOf("one", "two")),
                MarkdownBlock.Numbered(listOf("first", "second")),
                MarkdownBlock.Quote("quoted\nlines"),
                MarkdownBlock.Divider,
            ),
            MarkdownParser.parse(
                "# Title\n\n- one\n- two\n\n1. first\n2. second\n\n> quoted\n> lines\n\n---",
            ),
        )
    }

    @Test
    fun `paragraph lines are kept together`() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("line one\nline two")),
            MarkdownParser.parse("line one\nline two"),
        )
    }
}
