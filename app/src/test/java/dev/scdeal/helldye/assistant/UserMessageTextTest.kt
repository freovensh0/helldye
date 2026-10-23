package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class UserMessageTextTest {
    @Test
    fun `context blocks are hidden from the conversation`() {
        assertEquals(
            "Summarize this",
            UserMessageText.clean(
                "<screen_context>\nprivate screen\n</screen_context>\n\n" +
                    "<file name=\"notes.txt\">\nsecret\n</file>\n\nSummarize this",
            ),
        )
    }

    @Test
    fun `attachment-only prompts stay readable`() {
        assertEquals(
            "Use the attached context.",
            UserMessageText.clean("<file name=\"notes.txt\">\nsecret\n</file>"),
        )
    }
}
