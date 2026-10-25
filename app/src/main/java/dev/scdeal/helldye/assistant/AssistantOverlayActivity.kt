package dev.scdeal.helldye.assistant

/** Opaque assist-triggered overlay; the full session opens in [AssistantActivity]. */
class AssistantOverlayActivity : AssistantHostActivity() {
    override val overlay: Boolean = true
}
