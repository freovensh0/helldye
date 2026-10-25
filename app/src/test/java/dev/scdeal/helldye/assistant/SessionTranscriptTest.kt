package dev.scdeal.helldye.assistant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTranscriptTest {
    @Test
    fun `restores text, thinking, and tool results from a transcript`() {
        val transcript = JSONArray()
            .put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        "<screen_context>private</screen_context>\n\nHello",
                    ),
            )
            .put(
                JSONObject()
                    .put("role", "assistant")
                    .put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "thinking").put("thinking", "consider"))
                            .put(JSONObject().put("type", "text").put("text", "Looking"))
                            .put(
                                JSONObject()
                                    .put("type", "toolCall")
                                    .put("id", "call-1")
                                    .put("name", "read")
                                    .put("arguments", JSONObject().put("path", "a.txt")),
                            ),
                    ),
            )
            .put(
                JSONObject()
                    .put("role", "toolResult")
                    .put("toolCallId", "call-1")
                    .put(
                        "content",
                        JSONArray().put(JSONObject().put("type", "text").put("text", "file body")),
                    ),
            )
            .put(
                JSONObject()
                    .put("role", "assistant")
                    .put(
                        "content",
                        JSONArray().put(JSONObject().put("type", "text").put("text", "Done")),
                    ),
            )

        val restored = SessionTranscript.restore(transcript)

        assertEquals(
            listOf(ChatRole.User, ChatRole.Assistant, ChatRole.Tool, ChatRole.Assistant),
            restored.messages.map { it.role },
        )
        assertEquals("Hello", restored.messages[0].text)
        assertEquals("Looking", restored.messages[1].text)
        assertEquals("consider", restored.messages[1].thinking)
        assertEquals("tool-call-1", restored.messages[2].id)
        assertEquals("read", restored.messages[2].tool?.name)
        assertEquals("file body", restored.messages[2].tool?.result)
        assertFalse(restored.messages[2].tool?.running ?: true)
        assertEquals("Done", restored.messages[3].text)
        assertEquals(restored.messages[3].id, restored.trailingAssistantId)
        assertEquals(mapOf("call-1" to "tool-call-1"), restored.toolMessageIds)
    }

    @Test
    fun `a trailing tool card is not treated as a streaming assistant message`() {
        val transcript = JSONArray().put(
            JSONObject()
                .put("role", "assistant")
                .put(
                    "content",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "toolCall")
                            .put("id", "call-2")
                            .put("name", "bash")
                            .put("arguments", JSONObject()),
                    ),
                ),
        )

        val restored = SessionTranscript.restore(transcript)

        assertEquals(listOf(ChatRole.Tool), restored.messages.map { it.role })
        assertNull(restored.trailingAssistantId)
    }
}
