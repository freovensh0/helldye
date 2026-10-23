package dev.scdeal.helldye.assistant

/** Process-local draft that survives the overlay -> full session handoff. */
object AssistantDraftStore {
    @Volatile
    var attachments: List<Attachment> = emptyList()

    fun clear() {
        attachments = emptyList()
    }
}
