package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextTest {
    @Test
    fun `screen text is wrapped`() {
        val context = PromptContext.build("Settings / Battery", emptyList())

        assertTrue(context!!.contains("<screen_context>"))
        assertTrue(context.contains("Settings / Battery"))
    }

    @Test
    fun `explicit screen attachment replaces ambient screen text`() {
        val context = PromptContext.build(
            "ambient",
            listOf(Attachment.Screen("attached")),
        )

        assertTrue(context!!.contains("attached"))
        assertFalse(context.contains("ambient"))
    }

    @Test
    fun `text files are wrapped and nothing is returned without context`() {
        assertEquals(
            "<file name=\"notes.txt\">\nhello\n</file>",
            PromptContext.build(null, listOf(Attachment.Text("notes.txt", "hello"))),
        )
        assertEquals(null, PromptContext.build("  ", emptyList()))
    }
}
