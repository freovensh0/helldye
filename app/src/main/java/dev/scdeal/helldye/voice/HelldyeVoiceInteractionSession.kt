package dev.scdeal.helldye.voice

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import dev.scdeal.helldye.assistant.AssistantActivity
import dev.scdeal.helldye.assistant.AssistInbox

class HelldyeVoiceInteractionSession(
    context: Context,
) : VoiceInteractionSession(context) {
    private var launched = false

    override fun onShow(args: Bundle?, showFlags: Int) {
        AssistInbox.clear()
        launched = false
        launchAssistant()
    }

    override fun onHandleAssist(state: AssistState) {
        if (state.index != 0) return
        val structure = state.assistStructure ?: return
        AssistInbox.publish(structure.extractText())
    }

    private fun launchAssistant() {
        if (launched) return
        launched = true
        val intent = Intent(context, AssistantActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        startAssistantActivity(intent)
    }

    private fun AssistStructure.extractText(): String {
        val lines = mutableListOf<String>()
        for (index in 0 until windowNodeCount) {
            collect(getWindowNodeAt(index).rootViewNode, lines)
        }
        return lines
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString("\n")
            .take(SCREEN_TEXT_LIMIT)
    }

    private fun collect(node: AssistStructure.ViewNode?, lines: MutableList<String>) {
        if (node == null) return
        node.text?.let { lines += it.toString() }
        node.contentDescription?.let { lines += it.toString() }
        for (index in 0 until node.childCount) {
            collect(node.getChildAt(index), lines)
        }
    }

    private companion object {
        const val SCREEN_TEXT_LIMIT = 4_000
    }
}
