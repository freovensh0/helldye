package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRulesTest {
    private fun photo(bytes: Int): Attachment.Photo {
        // Base64 length for roughly `bytes` decoded bytes, padded to a multiple of 4.
        val base64Length = ((bytes + 2) / 3) * 4
        return Attachment.Photo("photo", "image/jpeg", "A".repeat(base64Length))
    }

    @Test
    fun `large images are scaled to the 1568 px long edge`() {
        assertEquals(1_568 to 1_176, ImageRules.scaledSize(4_032, 3_024))
        assertEquals(1_176 to 1_568, ImageRules.scaledSize(3_024, 4_032))
    }

    @Test
    fun `small images are not upscaled`() {
        assertEquals(800 to 600, ImageRules.scaledSize(800, 600))
    }

    @Test
    fun `at most four images fit`() {
        val existing = listOf(photo(100), photo(100), photo(100), photo(100))
        assertFalse(ImageRules.withinBudget(existing, photo(100)))
    }

    @Test
    fun `the six megabyte total is enforced`() {
        val existing = listOf(photo(1_500_000), photo(1_500_000), photo(1_400_000))
        assertTrue(ImageRules.withinBudget(existing, photo(1_000_000)))
        assertFalse(ImageRules.withinBudget(existing, photo(1_700_000)))
    }

    @Test
    fun `a capture must fit the long edge and per image limit`() {
        assertTrue(ImageRules.fitsCapture(1_568, 1_176, 1_500_000))
        assertFalse(ImageRules.fitsCapture(1_569, 1_176, 100))
        assertFalse(ImageRules.fitsCapture(800, 600, 1_500_001))
        assertFalse(ImageRules.fitsCapture(0, 600, 100))
        assertFalse(ImageRules.fitsCapture(800, 600, 0))
    }
}
