package dev.scdeal.helldye.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {
    private fun tool(id: String, failed: Boolean = false, running: Boolean = false) = ChatMessage(
        id = id,
        role = ChatRole.Tool,
        text = "bash",
        tool = ToolInfo(name = "bash", running = running, failed = failed),
    )

    @Test
    fun `consecutive tools collapse into one row`() {
        val items = Conversation.items(
            listOf(
                ChatMessage("u", ChatRole.User, "hi"),
                tool("t1"),
                tool("t2", failed = true),
                tool("t3"),
                ChatMessage("a", ChatRole.Assistant, "done"),
            ),
        )

        assertEquals(3, items.size)
        assertTrue(items[1] is ConversationItem.Tools)
        assertEquals(
            listOf("t1", "t2", "t3"),
            (items[1] as ConversationItem.Tools).messages.map { it.id },
        )
    }

    @Test
    fun `a single tool stays a turn`() {
        assertTrue(Conversation.items(listOf(tool("t1"))).single() is ConversationItem.Turn)
    }

    @Test
    fun `the collapsed summary counts tools failures and running`() {
        assertEquals("1 TOOL", Conversation.toolSummary(listOf(ToolInfo("bash", running = false))))
        assertEquals(
            "3 TOOLS · 1 FAILED",
            Conversation.toolSummary(
                listOf(ToolInfo("a", failed = true), ToolInfo("b"), ToolInfo("c")),
            ),
        )
        assertEquals(
            "2 TOOLS · 1 RUNNING",
            Conversation.toolSummary(
                listOf(ToolInfo("a", running = true), ToolInfo("b", running = false)),
            ),
        )
    }

    @Test
    fun `the transcript stays pinned only near the bottom`() {
        assertTrue(Conversation.shouldPinToBottom(lastVisibleIndex = 8, totalItems = 10))
        assertTrue(Conversation.shouldPinToBottom(lastVisibleIndex = 9, totalItems = 10))
        assertTrue(Conversation.shouldPinToBottom(lastVisibleIndex = 0, totalItems = 0))
        assertTrue(!Conversation.shouldPinToBottom(lastVisibleIndex = 3, totalItems = 10))
    }
}
