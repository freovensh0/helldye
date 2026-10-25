package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {
    @Test
    fun `code blocks and inline code are skipped`() {
        val markdown = "Run this:\n```sh\nrm -rf build\n```\nthen `make` again.\n\nDone."
        assertEquals("Run this: then again. Done.", SpeechText.plain(markdown))
    }

    @Test
    fun `whitespace collapses to one line`() {
        assertEquals("one two", SpeechText.plain("one\n\n  two"))
    }
}
