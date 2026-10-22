package dev.scdeal.helldye

import dev.scdeal.helldye.assistant.AssistantPhase
import dev.scdeal.helldye.assistant.AssistantViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantViewModelTest {
    @Test
    fun `submit records prompt and answer`() {
        val viewModel = AssistantViewModel()

        viewModel.submit("What is on my screen?", "Settings / Battery")

        val state = viewModel.state.value
        assertEquals(AssistantPhase.Answer, state.phase)
        assertEquals("What is on my screen?", state.transcript)
        assertTrue(state.answer.contains("${"Settings / Battery".length} characters"))
    }

    @Test
    fun `empty prompt is ignored`() {
        val viewModel = AssistantViewModel()

        viewModel.submit("   ", null)

        assertEquals(AssistantPhase.Ready, viewModel.state.value.phase)
    }

    @Test
    fun `microphone notice can be dismissed`() {
        val viewModel = AssistantViewModel()

        viewModel.onMic()
        assertTrue(viewModel.state.value.notice != null)

        viewModel.dismissNotice()
        assertNull(viewModel.state.value.notice)
    }
}
