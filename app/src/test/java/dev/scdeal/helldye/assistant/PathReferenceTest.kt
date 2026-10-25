package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class PathReferenceTest {
    @Test
    fun `a path is appended after the typed prompt`() {
        assertEquals(
            "what is this? @/sdcard/Pictures/a.png ",
            FilePaths.appendReference("what is this?", "/sdcard/Pictures/a.png"),
        )
    }

    @Test
    fun `an empty prompt becomes only the reference`() {
        assertEquals("@/sdcard/a.png ", FilePaths.appendReference("", "/sdcard/a.png"))
    }

    @Test
    fun `existing trailing whitespace is not doubled`() {
        assertEquals(
            "look at @/sdcard/a.png ",
            FilePaths.appendReference("look at ", "/sdcard/a.png"),
        )
    }
}
