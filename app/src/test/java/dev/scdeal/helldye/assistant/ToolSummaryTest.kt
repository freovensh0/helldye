package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolSummaryTest {
    @Test
    fun `durations switch to seconds`() {
        assertEquals("87ms", ToolSummary.duration(87))
        assertEquals("1.5s", ToolSummary.duration(1_500))
    }

    @Test
    fun `the summary prefers the result`() {
        assertEquals(
            "done",
            ToolSummary.line(ToolInfo(name = "bash", args = "{\"cmd\":\"ls\"}", result = "done\nmore")),
        )
        assertEquals(
            "{\"cmd\":\"ls\"}",
            ToolSummary.line(ToolInfo(name = "bash", args = "{\"cmd\":\"ls\"}")),
        )
    }

    @Test
    fun `the summary is clipped`() {
        assertEquals(160, ToolSummary.line(ToolInfo(name = "read", result = "x".repeat(400))).length)
        assertEquals("", ToolSummary.line(ToolInfo(name = "read")))
    }
}
