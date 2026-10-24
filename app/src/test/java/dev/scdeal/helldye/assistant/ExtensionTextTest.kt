package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionTextTest {
    @Test
    fun `terminal colors are stripped`() {
        assertEquals("PONYTAIL 46", ExtensionText.clean("\u001B[38;5;46mPONYTAIL 46\u001B[0m"))
    }

    @Test
    fun `osc titles and control characters are stripped`() {
        assertEquals(
            "status",
            ExtensionText.clean("\u001B]0;title\u0007sta\u0000tus"),
        )
    }

    @Test
    fun `plain text is trimmed`() {
        assertEquals("Turn 3 running", ExtensionText.clean("  Turn 3 running  "))
    }
}
