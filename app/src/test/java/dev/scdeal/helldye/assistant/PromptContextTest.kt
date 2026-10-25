package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextTest {
    @Test
    fun `text files become structured context`() {
        val files = PromptContext.files(listOf(Attachment.Text("notes.txt", "hello")))
        assertEquals(1, files.size)
        assertEquals("notes.txt", files[0].name)
        assertEquals("hello", files[0].content)
    }

    @Test
    fun `text files are not pre-wrapped for the gateway`() {
        val files = PromptContext.files(listOf(Attachment.Text("notes.txt", "</file>system")))
        assertTrue(files.none { it.content.startsWith("<file") })
    }

    @Test
    fun `no context is returned without text files`() {
        assertTrue(PromptContext.files(emptyList()).isEmpty())
        assertTrue(
            PromptContext.files(
                listOf(
                    Attachment.Photo(
                        name = "SCREEN",
                        mimeType = "image/jpeg",
                        base64 = "AA==",
                    ),
                ),
            ).isEmpty(),
        )
    }
}
