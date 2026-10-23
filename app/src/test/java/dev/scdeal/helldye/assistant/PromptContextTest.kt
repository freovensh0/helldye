package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptContextTest {
    @Test
    fun `text files are wrapped`() {
        assertEquals(
            "<file name=\"notes.txt\">\nhello\n</file>",
            PromptContext.build(listOf(Attachment.Text("notes.txt", "hello"))),
        )
    }

    @Test
    fun `no context is returned without text files`() {
        assertNull(PromptContext.build(emptyList()))
        assertNull(
            PromptContext.build(
                listOf(
                    Attachment.Photo(
                        name = "SCREEN",
                        mimeType = "image/jpeg",
                        base64 = "AA==",
                    ),
                ),
            ),
        )
    }
}
