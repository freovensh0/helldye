package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScreenQueryTest {
    @Test
    fun `an empty screen prompt uses the neutral question`() {
        assertEquals(ScreenQuery.DEFAULT_PROMPT, ScreenQuery.prompt("  "))
        assertEquals("What is wrong here?", ScreenQuery.prompt(" What is wrong here? "))
    }

    @Test
    fun `a screenshot submit builds no text screen context`() {
        val screenshot = Attachment.Photo("SCREEN", "image/jpeg", "AAAA")
        val photo = Attachment.Photo("photo", "image/jpeg", "BBBB")
        val attachments = listOf(screenshot, photo)

        assertTrue(ScreenQuery.textFiles(attachments).isEmpty())
        assertEquals(2, ScreenQuery.photos(attachments).size)
    }

    @Test
    fun `app sources never use a screen text path`() {
        val forbidden = listOf(
            "Assist" + "Structure",
            "Accessibility" + "Service",
            "Media" + "Projection",
            "READ_ASSIST_STRUCTURE" + "_SCREEN_CONTENT",
            "Text" + "Recognizer",
            "Input" + "Image",
        )
        val hits = appSourceRoot().walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                val text = file.readText()
                forbidden.filter { text.contains(it) }.map { "${file.name}: $it" }
            }
            .toList()

        assertTrue(hits.toString(), hits.isEmpty())
    }

    private fun appSourceRoot(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            for (relative in listOf("src/main/java", "app/src/main/java")) {
                val candidate = File(directory, relative)
                if (candidate.isDirectory) return candidate
            }
            directory = directory.parentFile
        }
        error("Could not find src/main/java from the test working directory.")
    }
}
