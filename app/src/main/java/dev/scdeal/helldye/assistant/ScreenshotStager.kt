package dev.scdeal.helldye.assistant

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScreenCaptureState { None, Ready, Blocked, Off, Sent }

/**
 * Process-local, memory-only staging for the one assist screenshot.
 *
 * The bitmap never leaves this object except into the ephemeral send that encodes it once. It is not
 * written to preferences, files, logs, or the recents thumbnail; dropping the reference is the delete.
 */
object ScreenshotStager {
    private val _state = MutableStateFlow(ScreenCaptureState.None)
    val state: StateFlow<ScreenCaptureState> = _state.asStateFlow()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    fun stage(shot: Bitmap) {
        _bitmap.value = shot
        _state.value = ScreenCaptureState.Ready
    }

    fun blocked() {
        _bitmap.value = null
        _state.value = ScreenCaptureState.Blocked
    }

    fun off() {
        _bitmap.value = null
        _state.value = ScreenCaptureState.Off
    }

    /** Returns the staged bitmap and moves to [ScreenCaptureState.Sent]; the caller encodes it once. */
    fun consume(): Bitmap? {
        if (_state.value != ScreenCaptureState.Ready) return null
        val shot = _bitmap.value
        _bitmap.value = null
        _state.value = ScreenCaptureState.Sent
        return shot
    }

    fun clear() {
        _bitmap.value = null
        _state.value = ScreenCaptureState.None
    }
}
