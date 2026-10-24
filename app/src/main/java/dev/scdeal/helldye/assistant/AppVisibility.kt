package dev.scdeal.helldye.assistant

/** Tracks whether any assistant window is visible, so extension notifications stay quiet in-app. */
object AppVisibility {
    private var active = 0

    val visible: Boolean
        get() = synchronized(this) { active > 0 }

    fun enter() = synchronized(this) { active++ }

    fun exit() = synchronized(this) { if (active > 0) active-- }
}
