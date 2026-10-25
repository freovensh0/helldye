package dev.scdeal.helldye.voice

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import dev.scdeal.helldye.assistant.AssistantOverlayActivity
import dev.scdeal.helldye.assistant.ScreenCaptureState
import dev.scdeal.helldye.assistant.ScreenshotStager

class HelldyeVoiceInteractionSession(
    context: Context,
) : VoiceInteractionSession(context) {
    private val appContext = context.applicationContext
    private val keyguard: KeyguardManager? by lazy {
        appContext.getSystemService(KeyguardManager::class.java)
    }

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        // The assistant UI is an activity, not the session window.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        // A new invocation never reuses the previous capture.
        ScreenshotStager.off()
        launchAssistant()
    }

    override fun onHandleAssist(state: AssistState) {
        // Assist content is ignored. Only onHandleScreenshot may stage an image.
        if (ScreenshotStager.state.value == ScreenCaptureState.None) {
            ScreenshotStager.off()
        }
        launchAssistant()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        // A locked device never stages a capture; the overlay shows the unlock gate instead.
        if (keyguard?.isKeyguardLocked == true) return
        if (screenshot == null) {
            ScreenshotStager.blocked()
        } else {
            ScreenshotStager.stage(screenshot)
        }
        launchAssistant()
    }

    private fun launchAssistant() {
        val intent = Intent(context, AssistantOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        startAssistantActivity(intent)
    }
}
