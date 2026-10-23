package dev.scdeal.helldye.voice

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import dev.scdeal.helldye.assistant.AssistantOverlayActivity
import dev.scdeal.helldye.assistant.AssistInbox

class HelldyeVoiceInteractionSession(
    context: Context,
) : VoiceInteractionSession(context) {
    private var launched = false

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        // The assistant UI is an activity, not the session window.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        launched = false
        launchAssistant()
    }

    override fun onHandleAssist(state: AssistState) {
        if (state.index != 0) return
        AssistInbox.markAssistReceived()
        if (!launched) launchAssistant()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        AssistInbox.publishScreenshot(screenshot)
        if (screenshot != null) AssistInbox.markAssistReceived()
    }

    private fun launchAssistant() {
        if (launched) return
        val intent = Intent(context, AssistantOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        startAssistantActivity(intent)
        launched = true
    }
}
