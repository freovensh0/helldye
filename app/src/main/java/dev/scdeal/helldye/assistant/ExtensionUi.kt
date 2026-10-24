package dev.scdeal.helldye.assistant

enum class NoticeTone { Info, Warning, Error }

data class ExtensionWidget(
    val lines: List<String>,
    val above: Boolean,
)

sealed interface ExtensionDialog {
    val id: String
    val title: String
    val timeoutMs: Long?

    data class Select(
        override val id: String,
        override val title: String,
        val options: List<String>,
        override val timeoutMs: Long? = null,
    ) : ExtensionDialog

    data class Confirm(
        override val id: String,
        override val title: String,
        val message: String,
        override val timeoutMs: Long? = null,
    ) : ExtensionDialog

    data class Input(
        override val id: String,
        override val title: String,
        val placeholder: String,
        override val timeoutMs: Long? = null,
    ) : ExtensionDialog

    data class Editor(
        override val id: String,
        override val title: String,
        val prefill: String,
        override val timeoutMs: Long? = null,
    ) : ExtensionDialog
}
