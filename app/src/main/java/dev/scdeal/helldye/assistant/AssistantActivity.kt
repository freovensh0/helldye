package dev.scdeal.helldye.assistant

/** Opaque, app-owned session view. */
class AssistantActivity : AssistantHostActivity() {
    override val overlay: Boolean = false
}
