package dev.scdeal.helldye.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.scdeal.helldye.gateway.GatewayClient
import dev.scdeal.helldye.gateway.GatewayCommand
import dev.scdeal.helldye.gateway.GatewayEvent
import dev.scdeal.helldye.gateway.GatewayException
import dev.scdeal.helldye.gateway.GatewayImage
import dev.scdeal.helldye.gateway.GatewayModel
import dev.scdeal.helldye.gateway.GatewaySession
import dev.scdeal.helldye.gateway.GatewaySessionStore
import dev.scdeal.helldye.gateway.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application)
    private val _state = MutableStateFlow(
        AssistantUiState(attachments = AssistantDraftStore.attachments),
    )
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private var client: GatewayClient? = null
    private var streamJob: Job? = null
    private var capabilitiesJob: Job? = null
    private var capabilitiesSessionId: String? = null
    private var activeAssistantId: String? = null
    private val toolMessages = mutableMapOf<String, String>()
    private val pendingUserEchoes = ConcurrentLinkedQueue<String>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val config = settings.load()
            if (config.url.isNotBlank() && GatewaySessionStore.sessionId != null &&
                GatewaySessionStore.fingerprint == config.fingerprint
            ) {
                val sessionId = GatewaySessionStore.sessionId ?: return@launch
                val gateway = GatewayClient(config)
                client = gateway
                startStream(gateway, sessionId)
                refreshCapabilities(gateway, sessionId, announce = false)
            }
        }
    }

    fun submit(text: String) {
        val prompt = text.trim()
        val attachments = _state.value.attachments
        if (prompt.isEmpty() && attachments.isEmpty()) return

        val config = settings.load()
        if (config.url.isBlank()) {
            notice("Configure the Pi gateway in Settings first.")
            return
        }

        val display = prompt.ifBlank { "Use the attached context." }
        addUserMessage(display)
        pendingUserEchoes.add(display)
        AssistantDraftStore.clear()
        _state.update {
            it.copy(attachments = emptyList(), streaming = true, error = null, notice = null)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gateway = ensureClient(config)
                val session = ensureSession(gateway, config)
                startStream(gateway, session.id)
                refreshCapabilities(gateway, session.id, announce = false)
                gateway.prompt(
                    sessionId = session.id,
                    message = prompt.ifBlank { "Use the attached context." },
                    context = PromptContext.build(attachments),
                    images = attachments.filterIsInstance<Attachment.Photo>().map {
                        GatewayImage(it.mimeType, it.base64)
                    },
                )
            } catch (error: Exception) {
                if (error is GatewayException && error.message?.contains("HTTP 404") == true) {
                    GatewaySessionStore.reset()
                    streamJob?.cancel()
                    streamJob = null
                }
                pendingUserEchoes.clear()
                _state.update {
                    it.copy(
                        streaming = false,
                        error = error.message ?: "Gateway request failed.",
                    )
                }
            }
        }
    }

    fun abort() {
        val sessionId = GatewaySessionStore.sessionId ?: return
        val gateway = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.abort(sessionId) }
        }
    }

    fun openModelPicker() {
        val config = settings.load()
        if (config.url.isBlank()) {
            notice("Configure the Pi gateway in Settings first.")
            return
        }
        _state.update { it.copy(modelPickerOpen = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gateway = ensureClient(config)
                val session = ensureSession(gateway, config)
                startStream(gateway, session.id)
                refreshCapabilities(gateway, session.id, announce = true)
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        modelPickerOpen = false,
                        error = error.message ?: "Could not load models.",
                    )
                }
            }
        }
    }

    fun closeModelPicker() {
        _state.update { it.copy(modelPickerOpen = false) }
    }

    fun openThinkingPicker() {
        val config = settings.load()
        if (config.url.isBlank()) {
            notice("Configure the Pi gateway in Settings first.")
            return
        }
        _state.update { it.copy(thinkingPickerOpen = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gateway = ensureClient(config)
                val session = ensureSession(gateway, config)
                startStream(gateway, session.id)
                refreshCapabilities(gateway, session.id, announce = true)
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        thinkingPickerOpen = false,
                        error = error.message ?: "Could not load thinking levels.",
                    )
                }
            }
        }
    }

    fun closeThinkingPicker() {
        _state.update { it.copy(thinkingPickerOpen = false) }
    }

    fun selectThinking(level: String) {
        val sessionId = GatewaySessionStore.sessionId ?: return
        val gateway = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.setThinkingLevel(sessionId, level) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            thinkingLevel = level,
                            thinkingPickerOpen = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not set the thinking level.")
                    }
                }
        }
    }

    fun selectModel(model: GatewayModel) {
        val sessionId = GatewaySessionStore.sessionId ?: return
        val gateway = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.setModel(sessionId, model) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            currentModel = model,
                            modelPickerOpen = false,
                            error = null,
                        )
                    }
                    runCatching { gateway.state(sessionId) }.onSuccess { sessionState ->
                        _state.update {
                            it.copy(
                                currentModel = sessionState.model ?: it.currentModel,
                                thinkingLevel = sessionState.thinkingLevel ?: it.thinkingLevel,
                            )
                        }
                    }
                    runCatching { gateway.thinkingLevels(sessionId) }.onSuccess { levels ->
                        _state.update { it.copy(thinkingLevels = levels) }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not switch models.")
                    }
                }
        }
    }

    fun newSession() {
        viewModelScope.launch(Dispatchers.IO) {
            streamJob?.cancel()
            streamJob = null
            capabilitiesJob?.cancel()
            capabilitiesJob = null
            capabilitiesSessionId = null
            val oldSession = GatewaySessionStore.sessionId
            val oldClient = client
            if (oldSession != null && oldClient != null) {
                runCatching { oldClient.deleteSession(oldSession) }
            }
            GatewaySessionStore.reset()
            client = null
            toolMessages.clear()
            pendingUserEchoes.clear()
            AssistantDraftStore.clear()
            _state.value = AssistantUiState()

            val config = settings.load()
            if (config.url.isBlank()) return@launch
            runCatching {
                val gateway = ensureClient(config)
                val session = ensureSession(gateway, config)
                startStream(gateway, session.id)
                refreshCapabilities(gateway, session.id, announce = false)
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "Could not start a new session.")
                }
            }
        }
    }

    fun addAttachment(attachment: Attachment) {
        _state.update { state ->
            val next = state.attachments + attachment
            AssistantDraftStore.attachments = next
            state.copy(attachments = next, notice = null)
        }
    }

    fun removeAttachment(index: Int) {
        _state.update { state ->
            val next = state.attachments.filterIndexed { itemIndex, _ -> itemIndex != index }
            AssistantDraftStore.attachments = next
            state.copy(attachments = next)
        }
    }

    fun notice(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null, error = null) }
    }

    override fun onCleared() {
        streamJob?.cancel()
        streamJob = null
        capabilitiesJob?.cancel()
        capabilitiesJob = null
        // Reattach by replaying the gateway buffer; the session itself stays alive.
        GatewaySessionStore.lastEventId = null
        super.onCleared()
    }

    private fun refreshCapabilities(
        gateway: GatewayClient,
        sessionId: String,
        announce: Boolean,
    ) {
        if (capabilitiesJob?.isActive == true) return
        if (!announce && capabilitiesSessionId == sessionId) return
        capabilitiesSessionId = sessionId
        capabilitiesJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(capabilitiesLoading = true) }
            val models = runCatching { gateway.models(sessionId) }
            val commands = runCatching { gateway.commands(sessionId) }
            val sessionState = runCatching { gateway.state(sessionId) }
            val stats = runCatching { gateway.stats(sessionId) }
            val levels = runCatching { gateway.thinkingLevels(sessionId) }
            _state.update { state ->
                state.copy(
                    models = models.getOrElse { state.models },
                    commands = commands.getOrElse { state.commands },
                    currentModel = sessionState.getOrNull()?.model ?: state.currentModel,
                    thinkingLevel = sessionState.getOrNull()?.thinkingLevel ?: state.thinkingLevel,
                    thinkingLevels = levels.getOrElse { state.thinkingLevels },
                    stats = stats.getOrElse { state.stats },
                    capabilitiesLoading = false,
                    error = if (announce && models.isFailure) {
                        models.exceptionOrNull()?.message ?: "Could not load models."
                    } else {
                        state.error
                    },
                )
            }
        }
    }

    private fun refreshStats() {
        val gateway = client ?: return
        val sessionId = GatewaySessionStore.sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.stats(sessionId) }
                .onSuccess { stats -> _state.update { it.copy(stats = stats) } }
        }
    }

    private fun ensureClient(config: dev.scdeal.helldye.gateway.GatewayConfig): GatewayClient {
        if (GatewaySessionStore.fingerprint != config.fingerprint) {
            streamJob?.cancel()
            streamJob = null
            client = null
            activeAssistantId = null
            toolMessages.clear()
            pendingUserEchoes.clear()
            capabilitiesSessionId = null
            GatewaySessionStore.reset()
        }
        return client ?: GatewayClient(config).also {
            client = it
            GatewaySessionStore.fingerprint = config.fingerprint
        }
    }

    private fun ensureSession(
        gateway: GatewayClient,
        config: dev.scdeal.helldye.gateway.GatewayConfig,
    ): GatewaySession {
        val existing = GatewaySessionStore.sessionId
        if (existing != null) {
            return GatewaySession(existing, "", config.cwd, "running")
        }
        val session = gateway.createSession()
        GatewaySessionStore.sessionId = session.id
        GatewaySessionStore.lastEventId = null
        GatewaySessionStore.fingerprint = config.fingerprint
        _state.update { it.copy(sessionId = session.id) }
        return session
    }

    private fun startStream(gateway: GatewayClient, sessionId: String) {
        if (streamJob?.isActive == true) return
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    gateway.events(sessionId, GatewaySessionStore.lastEventId).collect { event ->
                        event.id?.let { GatewaySessionStore.lastEventId = it }
                        handleEvent(event)
                    }
                    delay(1_000)
                } catch (error: Exception) {
                    if (!isActive) return@launch
                    if (error is GatewayException) {
                        _state.update { it.copy(error = error.message) }
                        if (error.message?.contains("HTTP 401") == true ||
                            error.message?.contains("HTTP 404") == true
                        ) {
                            GatewaySessionStore.reset()
                            streamJob = null
                            return@launch
                        }
                    }
                    delay(1_000)
                }
            }
        }
    }

    private fun handleEvent(event: GatewayEvent) {
        val payload = event.payload
        when (payload.optString("type")) {
            "agent_start" -> _state.update { it.copy(streaming = true) }
            "agent_settled" -> {
                activeAssistantId = null
                pendingUserEchoes.clear()
                _state.update { it.copy(streaming = false) }
                refreshStats()
            }
            "message_start" -> {
                val message = payload.optJSONObject("message") ?: return
                when (message.optString("role")) {
                    "user" -> {
                        if (pendingUserEchoes.isNotEmpty()) {
                            pendingUserEchoes.poll()
                        } else {
                            addUserMessage(UserMessageText.clean(messageText(message)))
                        }
                    }
                    "assistant" -> {
                        val id = message.optString("id").ifBlank { UUID.randomUUID().toString() }
                        activeAssistantId = id
                        _state.update {
                            it.copy(
                                messages = it.messages + ChatMessage(
                                    id = id,
                                    role = ChatRole.Assistant,
                                    text = messageText(message),
                                ),
                            )
                        }
                    }
                }
            }
            "message_update" -> {
                val update = payload.optJSONObject("assistantMessageEvent") ?: return
                if (update.optString("type") == "text_delta") {
                    appendAssistant(update.optString("delta"))
                }
            }
            "message_end" -> {
                val message = payload.optJSONObject("message") ?: return
                if (message.optString("role") == "assistant") {
                    val id = activeAssistantId
                    if (id != null) replaceAssistant(id, messageText(message))
                    activeAssistantId = null
                }
            }
            "tool_execution_start" -> {
                val callId = payload.optString("toolCallId")
                val name = payload.optString("toolName")
                val id = "tool-$callId-${UUID.randomUUID()}"
                toolMessages[callId] = id
                _state.update {
                    it.copy(messages = it.messages + ChatMessage(id, ChatRole.Tool, "$name RUNNING"))
                }
            }
            "tool_execution_end" -> {
                val callId = payload.optString("toolCallId")
                val id = toolMessages.remove(callId) ?: return
                val name = payload.optString("toolName")
                val status = if (payload.optBoolean("isError")) "FAILED" else "DONE"
                _state.update { state ->
                    state.copy(
                        messages = state.messages.map {
                            if (it.id == id) it.copy(text = "$name $status") else it
                        },
                    )
                }
            }
            "response" -> {
                if (payload.optString("command") == "prompt" && !payload.optBoolean("success", true)) {
                    pendingUserEchoes.clear()
                    _state.update {
                        it.copy(
                            streaming = false,
                            error = payload.optString("error").ifBlank { "Prompt rejected." },
                        )
                    }
                }
            }
            "gateway_exit" -> {
                pendingUserEchoes.clear()
                _state.update {
                    it.copy(
                        streaming = false,
                        error = "Pi process exited. Send again to start a new session.",
                    )
                }
                GatewaySessionStore.reset()
                streamJob?.cancel()
                streamJob = null
            }
        }
    }

    private fun appendAssistant(delta: String) {
        if (delta.isEmpty()) return
        val id = activeAssistantId ?: UUID.randomUUID().toString().also { newId ->
            activeAssistantId = newId
            _state.update {
                it.copy(messages = it.messages + ChatMessage(newId, ChatRole.Assistant, ""))
            }
        }
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(text = it.text + delta) else it
                },
            )
        }
    }

    private fun replaceAssistant(id: String, text: String) {
        if (text.isEmpty()) return
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(text = text) else it
                },
            )
        }
    }

    private fun addUserMessage(text: String) {
        if (text.isBlank()) return
        _state.update { state ->
            val last = state.messages.lastOrNull()
            if (last?.role == ChatRole.User && last.text == text) {
                state
            } else {
                state.copy(
                    messages = state.messages + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.User,
                        text = text,
                    ),
                )
            }
        }
    }

    private fun messageText(message: JSONObject): String {
        val content = message.opt("content") ?: return ""
        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val block = content.optJSONObject(index) ?: continue
                    if (block.optString("type") == "text") append(block.optString("text"))
                }
            }
            else -> ""
        }
    }

}
