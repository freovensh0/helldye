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
import java.util.concurrent.ConcurrentHashMap
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
    private val handledExtensionIds = ConcurrentHashMap.newKeySet<String>()
    private var dialogTimeoutJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val config = settings.load()
            if (config.url.isNotBlank() && GatewaySessionStore.sessionId != null &&
                GatewaySessionStore.fingerprint == config.fingerprint
            ) {
                val sessionId = GatewaySessionStore.sessionId ?: return@launch
                val gateway = GatewayClient(config)
                client = gateway
                val info = runCatching { gateway.session(sessionId) }
                if (info.isFailure) {
                    val message = info.exceptionOrNull()?.message.orEmpty()
                    if (message.contains("HTTP 404")) GatewaySessionStore.reset()
                    _state.update { it.copy(error = message.ifBlank { "Could not open the session." }) }
                    return@launch
                }
                _state.update { it.copy(sessionId = sessionId) }
                attach(gateway, sessionId, info.getOrNull()?.status)
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
                val session = ensureSession(gateway, config, name = display.take(40))
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

    fun compact() {
        val sessionId = GatewaySessionStore.sessionId ?: return
        val gateway = client ?: return
        _state.update {
            it.copy(
                compacting = true,
                notice = "Compacting context…",
                noticeTone = NoticeTone.Info,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.compact(sessionId) }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            compacting = false,
                            error = error.message ?: "Compaction failed.",
                        )
                    }
                }
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
        _state.update { it.copy(thinkingPickerOpen = true, capabilitiesLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gateway = ensureClient(config)
                val session = ensureSession(gateway, config)
                startStream(gateway, session.id)
                val levels = runCatching { gateway.thinkingLevels(session.id) }
                    .getOrElse { emptyList() }
                val sessionState = runCatching { gateway.state(session.id) }
                _state.update {
                    it.copy(
                        thinkingLevels = levels.ifEmpty { FALLBACK_THINKING_LEVELS },
                        thinkingLevel = sessionState.getOrNull()?.thinkingLevel ?: it.thinkingLevel,
                        capabilitiesLoading = false,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        thinkingPickerOpen = false,
                        capabilitiesLoading = false,
                        error = error.message ?: "Could not load thinking levels.",
                    )
                }
            }
        }
    }

    fun closeThinkingPicker() {
        _state.update { it.copy(thinkingPickerOpen = false) }
    }

    fun submitExtensionValue(value: String) {
        respondExtension(value = value)
    }

    fun submitExtensionConfirm(confirmed: Boolean) {
        respondExtension(confirmed = confirmed)
    }

    fun cancelExtension() {
        respondExtension(cancelled = true)
    }

    fun consumeEditorPrefill() {
        _state.update { it.copy(editorPrefill = null) }
    }

    private fun respondExtension(
        value: String? = null,
        confirmed: Boolean? = null,
        cancelled: Boolean = false,
    ) {
        val dialog = _state.value.extensionDialog ?: return
        val sessionId = GatewaySessionStore.sessionId ?: return
        val gateway = client ?: return
        dialogTimeoutJob?.cancel()
        _state.update { it.copy(extensionDialog = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                gateway.extensionResponse(sessionId, dialog.id, value, confirmed, cancelled)
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "Could not answer the extension.")
                }
            }
        }
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

    fun openSessions() {
        val config = settings.load()
        if (config.url.isBlank()) {
            notice("Configure the Pi gateway in Settings first.")
            return
        }
        _state.update { it.copy(sessionsOpen = true, sessionsLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val gateway = ensureClient(config)
                gateway.sessions()
            }.onSuccess { sessions ->
                _state.update { it.copy(sessions = sessions, sessionsLoading = false) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        sessionsLoading = false,
                        error = error.message ?: "Could not load sessions.",
                    )
                }
            }
        }
    }

    fun closeSessions() {
        _state.update { it.copy(sessionsOpen = false) }
    }

    fun switchSession(sessionId: String) {
        if (GatewaySessionStore.sessionId == sessionId) {
            closeSessions()
            return
        }
        val config = settings.load()
        if (config.url.isBlank()) return

        streamJob?.cancel()
        streamJob = null
        capabilitiesJob?.cancel()
        capabilitiesJob = null
        capabilitiesSessionId = null
        dialogTimeoutJob?.cancel()
        GatewaySessionStore.sessionId = sessionId
        GatewaySessionStore.lastEventId = null
        GatewaySessionStore.fingerprint = config.fingerprint
        activeAssistantId = null
        toolMessages.clear()
        pendingUserEchoes.clear()
        AssistantDraftStore.clear()
        _state.value = AssistantUiState(sessionId = sessionId)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val gateway = ensureClient(config)
                val info = gateway.session(sessionId)
                attach(gateway, sessionId, info.status)
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "Could not open the session.")
                }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        val gateway = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.deleteSession(sessionId) }
                .onSuccess {
                    if (GatewaySessionStore.sessionId == sessionId) {
                        streamJob?.cancel()
                        streamJob = null
                        GatewaySessionStore.reset()
                        _state.value = AssistantUiState()
                    }
                    runCatching { gateway.sessions() }.onSuccess { sessions ->
                        _state.update { it.copy(sessions = sessions) }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not delete the session.")
                    }
                }
        }
    }

    fun renameSession(sessionId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val gateway = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.renameSession(sessionId, trimmed) }
                .onSuccess { updated ->
                    _state.update { state ->
                        state.copy(
                            sessions = state.sessions.map {
                                if (it.id == updated.id) updated else it
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not rename the session.")
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
        dialogTimeoutJob?.cancel()
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
                    thinkingLevels = levels.getOrElse { state.thinkingLevels }
                        .ifEmpty { FALLBACK_THINKING_LEVELS },
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
        name: String? = null,
    ): GatewaySession {
        val existing = GatewaySessionStore.sessionId
        if (existing != null) {
            return GatewaySession(existing, "", config.cwd, "running")
        }
        val session = gateway.createSession(name)
        GatewaySessionStore.sessionId = session.id
        GatewaySessionStore.lastEventId = null
        GatewaySessionStore.fingerprint = config.fingerprint
        _state.update { it.copy(sessionId = session.id) }
        return session
    }

    private fun startStream(
        gateway: GatewayClient,
        sessionId: String,
        skipReplay: Boolean = false,
    ) {
        if (streamJob?.isActive == true) return
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            var skip = skipReplay
            while (isActive) {
                try {
                    gateway.events(
                        sessionId,
                        GatewaySessionStore.lastEventId,
                        replay = !skip,
                    ).collect { event ->
                        skip = false
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

    private fun attach(gateway: GatewayClient, sessionId: String, status: String?) {
        if (status == "stopped") {
            runCatching { gateway.messages(sessionId) }.onSuccess { restoreMessages(it) }
            startStream(gateway, sessionId, skipReplay = true)
        } else {
            startStream(gateway, sessionId)
        }
        refreshCapabilities(gateway, sessionId, announce = false)
    }

    private fun restoreMessages(messages: JSONArray) {
        val restored = mutableListOf<ChatMessage>()
        val tools = mutableMapOf<String, Int>()
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            when (message.optString("role")) {
                "user" -> {
                    val text = UserMessageText.clean(messageText(message))
                    if (text.isNotBlank()) {
                        restored += ChatMessage(
                            UUID.randomUUID().toString(),
                            ChatRole.User,
                            text,
                        )
                    }
                }
                "assistant" -> {
                    val text = messageText(message)
                    val thinking = messageThinking(message)
                    if (text.isNotBlank() || thinking.isNotBlank()) {
                        restored += ChatMessage(
                            UUID.randomUUID().toString(),
                            ChatRole.Assistant,
                            text,
                            thinking,
                        )
                    }
                    val content = message.optJSONArray("content") ?: continue
                    for (blockIndex in 0 until content.length()) {
                        val block = content.optJSONObject(blockIndex) ?: continue
                        if (block.optString("type") != "toolCall") continue
                        val callId = block.optString("id")
                        if (callId.isBlank()) continue
                        val name = block.optString("name")
                        tools[callId] = restored.size
                        restored += ChatMessage(
                            id = "tool-$callId",
                            role = ChatRole.Tool,
                            text = name,
                            tool = ToolInfo(
                                name = name,
                                args = clip(block.optJSONObject("arguments")?.toString(2).orEmpty()),
                            ),
                        )
                    }
                }
                "toolResult" -> {
                    val callId = message.optString("toolCallId")
                    val at = tools[callId] ?: continue
                    val tool = restored[at].tool ?: continue
                    restored[at] = restored[at].copy(
                        tool = tool.copy(
                            result = clip(toolResultText(message)),
                            running = false,
                            failed = message.optBoolean("isError"),
                        ),
                    )
                }
            }
        }
        activeAssistantId = null
        _state.update { it.copy(messages = restored) }
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
            "extension_ui_request" -> handleExtensionRequest(payload)
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
                when (update.optString("type")) {
                    "text_delta" -> appendAssistant(update.optString("delta"))
                    "text_end" -> replaceAssistantText(update.optString("content"))
                    "thinking_delta" -> appendThinking(update.optString("delta"))
                    "thinking_end" -> replaceThinking(update.optString("content"))
                }
            }
            "message_end" -> {
                val message = payload.optJSONObject("message") ?: return
                if (message.optString("role") == "assistant") {
                    val id = activeAssistantId
                    if (id != null) {
                        finalizeAssistant(id, messageText(message), messageThinking(message))
                    }
                    activeAssistantId = null
                }
            }
            "tool_execution_start" -> {
                val callId = payload.optString("toolCallId")
                val name = payload.optString("toolName")
                val id = "tool-$callId-${UUID.randomUUID()}"
                toolMessages[callId] = id
                _state.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            id = id,
                            role = ChatRole.Tool,
                            text = name,
                            tool = ToolInfo(
                                name = name,
                                args = clip(payload.optJSONObject("args")?.toString(2).orEmpty()),
                            ),
                        ),
                    )
                }
            }
            "tool_execution_update" -> {
                val id = toolMessages[payload.optString("toolCallId")] ?: return
                val result = toolResultText(payload.optJSONObject("partialResult"))
                _state.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            val tool = message.tool
                            if (message.id == id && tool != null) {
                                message.copy(tool = tool.copy(result = clip(result)))
                            } else {
                                message
                            }
                        },
                    )
                }
            }
            "tool_execution_end" -> {
                val callId = payload.optString("toolCallId")
                val id = toolMessages.remove(callId) ?: return
                val result = clip(toolResultText(payload.optJSONObject("result")))
                val failed = payload.optBoolean("isError")
                val duration = payload.optLong("durationMs").takeIf { it > 0 }
                _state.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            val tool = message.tool
                            if (message.id == id && tool != null) {
                                message.copy(
                                    tool = tool.copy(
                                        result = result,
                                        running = false,
                                        failed = failed,
                                        durationMs = duration,
                                    ),
                                )
                            } else {
                                message
                            }
                        },
                    )
                }
            }
            "queue_update" -> {
                val steering = payload.optJSONArray("steering")?.length() ?: 0
                val followUp = payload.optJSONArray("followUp")?.length() ?: 0
                _state.update { it.copy(queued = steering + followUp) }
            }
            "thinking_level_changed" -> {
                val level = payload.optString("level").takeIf { it.isNotBlank() }
                if (level != null) _state.update { it.copy(thinkingLevel = level) }
            }
            "compaction_start" -> _state.update { it.copy(compacting = true) }
            "compaction_end" -> {
                _state.update { it.copy(compacting = false, notice = null) }
                refreshStats()
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
                activeAssistantId = null
                pendingUserEchoes.clear()
                _state.update {
                    it.copy(
                        streaming = false,
                        notice = "Pi stopped. Send again to resume this session.",
                        noticeTone = NoticeTone.Info,
                    )
                }
            }
        }
    }

    private fun handleExtensionRequest(payload: JSONObject) {
        val id = payload.optString("id").ifBlank { return }
        if (!handledExtensionIds.add(id)) return
        val timeout = payload.optLong("timeout").takeIf { it > 0 }
        when (payload.optString("method")) {
            "notify" -> {
                val message = ExtensionText.clean(payload.optString("message")).ifBlank { return }
                val tone = when (payload.optString("notifyType")) {
                    "warning" -> NoticeTone.Warning
                    "error" -> NoticeTone.Error
                    else -> NoticeTone.Info
                }
                _state.update { it.copy(notice = message, noticeTone = tone) }
            }
            "setStatus" -> {
                val key = payload.optString("statusKey").ifBlank { return }
                val text = ExtensionText.clean(payload.optString("statusText"))
                _state.update { state ->
                    state.copy(
                        extensionStatus = if (text.isBlank()) {
                            state.extensionStatus - key
                        } else {
                            state.extensionStatus + (key to text)
                        },
                    )
                }
            }
            "setWidget" -> {
                val key = payload.optString("widgetKey").ifBlank { return }
                val lines = payload.optJSONArray("widgetLines")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            ExtensionText.clean(array.optString(index)).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
                val above = payload.optString("widgetPlacement", "aboveEditor") != "belowEditor"
                _state.update { state ->
                    state.copy(
                        extensionWidgets = if (lines.isEmpty()) {
                            state.extensionWidgets - key
                        } else {
                            state.extensionWidgets + (key to ExtensionWidget(lines, above))
                        },
                    )
                }
            }
            "set_editor_text" -> {
                val text = payload.optString("text")
                if (text.isNotBlank()) _state.update { it.copy(editorPrefill = text) }
            }
            "select" -> {
                val options = payload.optJSONArray("options")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            ExtensionText.clean(array.optString(index)).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
                showExtensionDialog(
                    ExtensionDialog.Select(
                        id = id,
                        title = ExtensionText.clean(payload.optString("title")),
                        options = options,
                        timeoutMs = timeout,
                    ),
                )
            }
            "confirm" -> showExtensionDialog(
                ExtensionDialog.Confirm(
                    id = id,
                    title = ExtensionText.clean(payload.optString("title")),
                    message = ExtensionText.clean(payload.optString("message")),
                    timeoutMs = timeout,
                ),
            )
            "input" -> showExtensionDialog(
                ExtensionDialog.Input(
                    id = id,
                    title = ExtensionText.clean(payload.optString("title")),
                    placeholder = ExtensionText.clean(payload.optString("placeholder")),
                    timeoutMs = timeout,
                ),
            )
            "editor" -> showExtensionDialog(
                ExtensionDialog.Editor(
                    id = id,
                    title = ExtensionText.clean(payload.optString("title")),
                    prefill = payload.optString("prefill"),
                    timeoutMs = timeout,
                ),
            )
        }
    }

    private fun showExtensionDialog(dialog: ExtensionDialog) {
        dialogTimeoutJob?.cancel()
        _state.update { it.copy(extensionDialog = dialog) }
        val timeout = dialog.timeoutMs ?: return
        dialogTimeoutJob = viewModelScope.launch {
            delay(timeout)
            _state.update {
                if (it.extensionDialog?.id == dialog.id) it.copy(extensionDialog = null) else it
            }
        }
    }

    private fun appendAssistant(delta: String) {
        if (delta.isEmpty()) return
        val id = ensureAssistant()
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(text = it.text + delta) else it
                },
            )
        }
    }

    private fun appendThinking(delta: String) {
        if (delta.isEmpty()) return
        val id = ensureAssistant()
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(thinking = it.thinking + delta) else it
                },
            )
        }
    }

    private fun replaceAssistantText(content: String) {
        if (content.isEmpty()) return
        val id = ensureAssistant()
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(text = content) else it
                },
            )
        }
    }

    private fun replaceThinking(content: String) {
        if (content.isEmpty()) return
        val id = ensureAssistant()
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(thinking = content) else it
                },
            )
        }
    }

    private fun finalizeAssistant(id: String, text: String, thinking: String) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id != id) {
                        it
                    } else {
                        it.copy(
                            text = text.ifEmpty { it.text },
                            thinking = thinking.ifEmpty { it.thinking },
                        )
                    }
                },
            )
        }
    }

    private fun ensureAssistant(): String {
        activeAssistantId?.let { return it }
        val id = UUID.randomUUID().toString()
        activeAssistantId = id
        _state.update {
            it.copy(messages = it.messages + ChatMessage(id, ChatRole.Assistant, ""))
        }
        return id
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

    private fun messageThinking(message: JSONObject): String {
        val content = message.opt("content")
        if (content !is JSONArray) return ""
        return buildString {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                if (block.optString("type") == "thinking") append(block.optString("thinking"))
            }
        }
    }

    private fun toolResultText(result: JSONObject?): String {
        if (result == null) return ""
        return when (val content = result.opt("content")) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val block = content.optJSONObject(index) ?: continue
                    if (block.optString("type") != "text") continue
                    if (isNotEmpty()) append('\n')
                    append(block.optString("text"))
                }
            }
            else -> result.toString(2)
        }
    }

    private fun clip(text: String): String =
        if (text.length <= 16_000) text else text.take(16_000) + "\n…"

}

/** Used when the gateway predates the thinking-level endpoint; Pi still validates the chosen level. */
private val FALLBACK_THINKING_LEVELS = listOf("off", "minimal", "low", "medium", "high")
