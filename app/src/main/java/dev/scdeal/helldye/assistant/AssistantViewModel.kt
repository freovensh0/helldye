package dev.scdeal.helldye.assistant

import android.app.Application
import android.speech.tts.TextToSpeech
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
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
    private var screenJob: Job? = null
    private var screenCollectorJob: Job? = null
    @Volatile
    private var screenSessionId: String? = null
    @Volatile
    private var screenAssistantId: String? = null
    private var activeCwd: String? = null
    private var capabilitiesJob: Job? = null
    private var capabilitiesSessionId: String? = null
    private var activeAssistantId: String? = null
    private val toolMessages = mutableMapOf<String, String>()
    private val pendingUserEchoes = ConcurrentLinkedQueue<String>()
    private var olderCursor: Int? = null
    private var draftJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private val handledExtensionIds = ConcurrentHashMap.newKeySet<String>()
    private var dialogTimeoutJob: Job? = null

    init {
        GatewaySessionStore.attach(settings)
        viewModelScope.launch(Dispatchers.IO) {
            val pending = settings.loadPendingSubmit()
            if (pending != null) {
                settings.clearPendingSubmit()
                _state.update {
                    it.copy(
                        editorPrefill = pending,
                        notice = "The last send was interrupted. Review it and retry.",
                    )
                }
            }
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
                activeCwd = info.getOrNull()?.cwd?.takeIf { cwd -> cwd.isNotBlank() }
                attach(gateway, sessionId)
            }
        }
    }

    /** Read-aloud is off by default; only the final answer is spoken, never code or screen context. */
    fun readLastAnswer() {
        if (!settings.readAnswersEnabled()) {
            notice("Enable \"Read answers\" in Settings first.")
            return
        }
        val message = _state.value.messages.lastOrNull { it.role == ChatRole.Assistant } ?: return
        val text = SpeechText.plain(message.text)
        if (text.isBlank()) return
        pendingSpeech = text
        if (tts == null) {
            tts = TextToSpeech(getApplication()) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.getDefault()
                    speakPending()
                } else {
                    pendingSpeech = null
                    notice("Text-to-speech is unavailable.")
                }
            }
        } else if (ttsReady) {
            speakPending()
        }
    }

    private fun speakPending() {
        val text = pendingSpeech ?: return
        pendingSpeech = null
        tts?.speak(text.take(8_000), TextToSpeech.QUEUE_FLUSH, null, "helldye-answer")
    }

    private fun stopSpeaking() {
        pendingSpeech = null
        tts?.stop()
    }

    /** Debounced per-session draft persistence; the secure write never blocks typing. */
    fun updateDraft(text: String) {
        val sessionId = GatewaySessionStore.sessionId ?: return
        draftJob?.cancel()
        draftJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            settings.saveDraft(sessionId, text)
        }
    }

    fun submit(text: String) {
        val prompt = text.trim()
        val attachments = _state.value.attachments
        val screenReady = ScreenshotStager.state.value == ScreenCaptureState.Ready
        if (prompt.isEmpty() && attachments.isEmpty() && !screenReady) return

        val config = settings.load()
        if (config.url.isBlank()) {
            notice("Configure the Pi gateway in Settings first.")
            return
        }

        if (screenReady) {
            val photos = ScreenQuery.photos(attachments)
            if (photos.size + 1 > ImageRules.MAX_IMAGES) {
                notice("At most ${ImageRules.MAX_IMAGES} images can be attached.")
                return
            }
            val shot = ScreenshotStager.consume() ?: return
            submitScreenQuery(config, prompt, attachments, shot)
            return
        }

        // A blocked/off strip is dismissed by a text-only send; no image is claimed.
        if (ScreenshotStager.state.value != ScreenCaptureState.None) ScreenshotStager.clear()

        val display = prompt.ifBlank { "Use the attached context." }
        draftJob?.cancel()
        GatewaySessionStore.sessionId?.let { settings.clearDraft(it) }
        settings.savePendingSubmit(display)
        val optimisticId = addUserMessage(display)
        pendingUserEchoes.add(display)
        AssistantDraftStore.clear()
        _state.update {
            it.copy(attachments = emptyList(), streaming = true, error = null, notice = null)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gateway = ensureClient(config)
                val session = ensureSession(gateway, config)
                gateway.prompt(
                    sessionId = session.id,
                    message = prompt.ifBlank { "Use the attached context." },
                    contextFiles = PromptContext.files(attachments),
                    images = attachments.filterIsInstance<Attachment.Photo>().map {
                        GatewayImage(it.mimeType, it.base64)
                    },
                )
                // The gateway buffers events, so subscribing after acceptance cannot lose a turn.
                startStream(gateway, session.id)
                refreshCapabilities(gateway, session.id, announce = false)
            } catch (error: Exception) {
                if (error is GatewayException && error.message?.contains("HTTP 404") == true) {
                    GatewaySessionStore.reset()
                    streamJob?.cancel()
                    streamJob = null
                }
                pendingUserEchoes.clear()
                settings.clearPendingSubmit()
                // Roll the optimistic turn back and keep the draft: nothing was delivered.
                AssistantDraftStore.attachments = attachments
                _state.update {
                    it.copy(
                        streaming = false,
                        messages = if (optimisticId == null) {
                            it.messages
                        } else {
                            it.messages.filterNot { message -> message.id == optimisticId }
                        },
                        attachments = attachments,
                        editorPrefill = prompt,
                        error = error.message ?: "Gateway request failed.",
                    )
                }
            }
        }
    }

    /**
     * A screenshot submit is one-shot: an ephemeral gateway session with `--no-session` and the
     * read-only tools. Its answer lives in [AssistantUiState.screenMessages] and is never restored
     * from `/messages` or added to the persistent transcript.
     */
    private fun submitScreenQuery(
        config: dev.scdeal.helldye.gateway.GatewayConfig,
        prompt: String,
        attachments: List<Attachment>,
        bitmap: android.graphics.Bitmap,
    ) {
        val display = ScreenQuery.prompt(prompt)
        _state.update {
            it.copy(
                screenMessages = listOf(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.User,
                        text = display,
                    ),
                ),
                screenQuery = true,
                streaming = true,
                attachments = emptyList(),
                error = null,
                notice = null,
            )
        }
        AssistantDraftStore.clear()
        draftJob?.cancel()
        GatewaySessionStore.sessionId?.let { settings.clearDraft(it) }
        screenAssistantId = null

        screenJob = viewModelScope.launch(Dispatchers.IO) {
            var sessionId: String? = null
            try {
                val photo = ScreenCapture.encode(bitmap)
                    ?: throw IllegalStateException("The screenshot is too large to send.")
                val totalBytes = ImageRules.decodedBytes(photo.base64) +
                    ScreenQuery.photos(attachments).sumOf {
                        ImageRules.decodedBytes(it.base64)
                    }
                if (totalBytes > ImageRules.MAX_TOTAL_BYTES) {
                    throw IllegalStateException(
                        "Images must total at most ${ImageRules.MAX_TOTAL_BYTES / 1_000_000} MB.",
                    )
                }
                val gateway = ensureClient(config)
                val session = gateway.createSession(
                    cwd = (activeCwd ?: config.cwd).takeIf { it.isNotBlank() },
                    ephemeral = true,
                )
                sessionId = session.id
                screenSessionId = session.id

                val connected = CompletableDeferred<Unit>()
                val collector = viewModelScope.async(Dispatchers.IO) {
                    gateway.events(session.id, null, replay = true) {
                        connected.complete(Unit)
                    }.takeWhile { event ->
                        val type = event.payload.optString("type")
                        type != "agent_settled" && type != "gateway_exit"
                    }.collect { handleScreenEvent(it) }
                }
                screenCollectorJob = collector
                collector.invokeOnCompletion { cause ->
                    if (cause != null) connected.completeExceptionally(cause)
                }
                try {
                    withTimeout(10_000) { connected.await() }
                } catch (error: TimeoutCancellationException) {
                    collector.cancel()
                    throw IllegalStateException("The screen query did not connect.")
                } catch (error: Exception) {
                    collector.cancel()
                    throw error
                }

                settings.savePendingSubmit(display)
                gateway.prompt(
                    sessionId = session.id,
                    message = display,
                    contextFiles = PromptContext.files(ScreenQuery.textFiles(attachments)),
                    images = buildList {
                        add(GatewayImage(photo.mimeType, photo.base64))
                        ScreenQuery.photos(attachments).forEach {
                            add(GatewayImage(it.mimeType, it.base64))
                        }
                    },
                )
                collector.await()
                settings.clearPendingSubmit()
                _state.update { it.copy(streaming = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                screenCollectorJob?.cancel()
                settings.clearPendingSubmit()
                ScreenshotStager.clear()
                // The screenshot is gone by contract; restore the text and non-screen attachments.
                AssistantDraftStore.attachments = attachments
                _state.update {
                    it.copy(
                        streaming = false,
                        screenQuery = false,
                        screenMessages = emptyList(),
                        attachments = attachments,
                        editorPrefill = prompt,
                        error = error.message ?: "Screen query failed.",
                    )
                }
            } finally {
                val id = sessionId
                if (id != null) {
                    runCatching { client?.deleteSession(id) }
                    if (screenSessionId == id) screenSessionId = null
                }
            }
        }
    }

    /** Prepend the previous transcript page; pages are index slices, so nothing overlaps. */
    fun loadOlder() {
        val before = olderCursor ?: return
        if (!_state.value.hasOlder || _state.value.loadingOlder) return
        val gateway = client ?: return
        val sessionId = GatewaySessionStore.sessionId ?: return
        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val page = runCatching { gateway.messages(sessionId, limit = 500, before = before) }
                .getOrNull()
            if (page == null || GatewaySessionStore.sessionId != sessionId) {
                _state.update { it.copy(loadingOlder = false) }
                return@launch
            }
            val older = SessionTranscript.restore(page.messages)
            older.toolMessageIds.forEach { (callId, id) -> toolMessages.putIfAbsent(callId, id) }
            olderCursor = (before - page.messages.length()).coerceAtLeast(0)
            _state.update {
                it.copy(
                    messages = older.messages + it.messages,
                    hasOlder = page.hasMore,
                    loadingOlder = false,
                )
            }
        }
    }

    fun removeScreenshot() {
        ScreenshotStager.clear()
    }

    /** Close/hide/background: drop the transient screen answer and delete its ephemeral session. */
    fun clearScreenQuery() {
        ScreenshotStager.clear()
        val id = screenSessionId
        val wasStreaming = screenJob?.isActive == true
        screenSessionId = null
        screenJob?.cancel()
        screenJob = null
        screenCollectorJob?.cancel()
        screenCollectorJob = null
        screenAssistantId = null
        if (id != null || _state.value.screenQuery) {
            _state.update {
                it.copy(
                    screenMessages = emptyList(),
                    screenQuery = false,
                    streaming = if (wasStreaming) false else it.streaming,
                )
            }
        }
        if (id != null) {
            val gateway = client ?: return
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { gateway.deleteSession(id) }
            }
        }
    }

    fun abort() {
        val sessionId = screenSessionId ?: GatewaySessionStore.sessionId ?: return
        val gateway = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.abort(sessionId) }
                .onSuccess {
                    // Keep STOPPING visible until the gateway settles the turn.
                    _state.update { it.copy(stopping = true) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not stop the turn.")
                    }
                }
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
        clearScreenQuery()
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
        stopSpeaking()
        if (GatewaySessionStore.sessionId == sessionId) {
            closeSessions()
            return
        }
        val config = settings.load()
        if (config.url.isBlank()) return

        clearScreenQuery()
        streamJob?.cancel()
        streamJob = null
        capabilitiesJob?.cancel()
        capabilitiesJob = null
        capabilitiesSessionId = null
        dialogTimeoutJob?.cancel()
        GatewaySessionStore.sessionId = sessionId
        GatewaySessionStore.lastEventId = null
        GatewaySessionStore.fingerprint = config.fingerprint
        olderCursor = null
        activeAssistantId = null
        toolMessages.clear()
        pendingUserEchoes.clear()
        AssistantDraftStore.clear()
        _state.value = AssistantUiState(sessionId = sessionId)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val gateway = ensureClient(config)
                val info = gateway.session(sessionId)
                activeCwd = info.cwd.takeIf { it.isNotBlank() }
                attach(gateway, sessionId)
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "Could not open the session.")
                }
            }
        }
    }

    fun openDiff() {
        val gateway = client ?: return
        val sessionId = GatewaySessionStore.sessionId ?: return
        _state.update { it.copy(diffOpen = true, diffLoading = true, diffError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { gateway.diff(sessionId) }
            if (GatewaySessionStore.sessionId != sessionId) return@launch
            _state.update {
                it.copy(
                    diffLoading = false,
                    diff = result.getOrNull() ?: it.diff,
                    diffError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun closeDiff() {
        _state.update { it.copy(diffOpen = false) }
    }

    fun openTree() {
        val gateway = client ?: return
        val sessionId = GatewaySessionStore.sessionId ?: return
        _state.update { it.copy(treeOpen = true, treeLoading = true, treeError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { gateway.tree(sessionId) }
            if (GatewaySessionStore.sessionId != sessionId) return@launch
            _state.update {
                it.copy(
                    treeLoading = false,
                    tree = result.getOrNull() ?: it.tree,
                    treeError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun closeTree() {
        _state.update { it.copy(treeOpen = false) }
    }

    fun exportHtml() {
        val gateway = client ?: return
        val sessionId = GatewaySessionStore.sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.exportHtml(sessionId) }
                .onSuccess { html -> _state.update { it.copy(exportHtml = html) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not export the session.")
                    }
                }
        }
    }

    fun consumeExport() {
        _state.update { it.copy(exportHtml = null) }
    }

    fun importSessions() {
        val config = settings.load()
        if (config.url.isBlank()) {
            notice("Configure the Pi gateway in Settings first.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ensureClient(config).importSessions() }
                .onSuccess { imported ->
                    runCatching { ensureClient(config).sessions() }.onSuccess { sessions ->
                        _state.update { it.copy(sessions = sessions) }
                    }
                    notice(
                        if (imported.isEmpty()) {
                            "No new sessions found on the host."
                        } else {
                            "Imported ${imported.size} session(s) from the host."
                        },
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not import sessions.")
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
                        clearScreenQuery()
                        GatewaySessionStore.reset()
                        _state.value = AssistantUiState()
                    }
                    settings.clearDraft(sessionId)
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
        if (attachment is Attachment.Photo) {
            val photos = _state.value.attachments.filterIsInstance<Attachment.Photo>()
            if (!ImageRules.withinBudget(photos, attachment)) {
                val message = if (photos.size >= ImageRules.MAX_IMAGES) {
                    "At most ${ImageRules.MAX_IMAGES} images can be attached."
                } else {
                    "Images must total at most ${ImageRules.MAX_TOTAL_BYTES / 1_000_000} MB."
                }
                notice(message)
                return
            }
        }
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
        screenJob?.cancel()
        screenJob = null
        screenCollectorJob?.cancel()
        screenCollectorJob = null
        capabilitiesJob?.cancel()
        capabilitiesJob = null
        dialogTimeoutJob?.cancel()
        draftJob?.cancel()
        stopSpeaking()
        tts?.shutdown()
        tts = null
        // The session stays alive with the gateway; the next attach restores it from the transcript.
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
            // Read gateway state first; every capability route below needs a live Pi child.
            val snapshot = runCatching { gateway.state(sessionId) }.getOrNull()
            if (snapshot != null) {
                GatewaySessionStore.epoch = snapshot.epoch ?: GatewaySessionStore.epoch
                _state.update { state ->
                    state.copy(
                        currentModel = snapshot.model ?: state.currentModel,
                        thinkingLevel = snapshot.thinkingLevel ?: state.thinkingLevel,
                        streaming = state.streaming || snapshot.busy || snapshot.stopping,
                        stopping = snapshot.stopping,
                    )
                }
            }
            if (snapshot?.started != true) {
                // Opening a stopped session must not spawn Pi. A later prompt retries.
                capabilitiesSessionId = null
                _state.update { it.copy(capabilitiesLoading = false) }
                return@launch
            }
            val models = runCatching { gateway.models(sessionId) }
            val commands = runCatching { gateway.commands(sessionId) }
            val stats = runCatching { gateway.stats(sessionId) }
            val levels = runCatching { gateway.thinkingLevels(sessionId) }
            _state.update { state ->
                state.copy(
                    models = models.getOrElse { state.models },
                    commands = commands.getOrElse { state.commands },
                    currentModel = snapshot?.model ?: state.currentModel,
                    thinkingLevel = snapshot?.thinkingLevel ?: state.thinkingLevel,
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
            activeCwd = null
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
        activeCwd = session.cwd.takeIf { it.isNotBlank() }
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
                var notStarted = false
                try {
                    gateway.events(
                        sessionId,
                        GatewaySessionStore.lastEventId,
                        replay = !skip,
                    ).collect { event ->
                        if (GatewaySessionStore.sessionId != sessionId) return@collect
                        skip = false
                        if (event.payload.optString("type") == "not_started") {
                            notStarted = true
                            return@collect
                        }
                        event.id?.let { GatewaySessionStore.lastEventId = it }
                        handleEvent(event)
                    }
                    if (notStarted) {
                        streamJob = null
                        return@launch
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

    private fun attach(gateway: GatewayClient, sessionId: String) {
        // Gateway state tells us whether Pi is alive and busy without starting it.
        val snapshot = runCatching { gateway.state(sessionId) }.getOrNull()
        if (GatewaySessionStore.sessionId != sessionId) return
        if (snapshot != null) {
            GatewaySessionStore.lastEventId = GatewaySessionStore.reconcileCursor(
                storedEpoch = GatewaySessionStore.epoch,
                stateEpoch = snapshot.epoch,
                cursor = GatewaySessionStore.lastEventId,
            )
            GatewaySessionStore.epoch = snapshot.epoch ?: GatewaySessionStore.epoch
            _state.update {
                it.copy(
                    streaming = it.streaming || snapshot.busy || snapshot.stopping,
                    stopping = snapshot.stopping,
                )
            }
        }
        // The transcript is authoritative; the event ring is only a live tail and can be cleared
        // when Pi restarts. Always restore first, then resume the stream after that snapshot.
        val transcript = runCatching { gateway.messages(sessionId) }.getOrNull()
        if (GatewaySessionStore.sessionId != sessionId) return
        if (transcript != null) {
            // A prompt sent while the transcript loaded already owns the conversation.
            val restored = _state.value.messages.isEmpty()
            if (restored) {
                restoreMessages(transcript.messages)
                GatewaySessionStore.lastEventId = transcript.eventId
                GatewaySessionStore.epoch = transcript.epoch ?: GatewaySessionStore.epoch
                olderCursor = (transcript.total - transcript.messages.length()).coerceAtLeast(0)
                val draft = settings.loadDraft(sessionId)
                _state.update {
                    it.copy(
                        hasOlder = transcript.hasMore,
                        editorPrefill = draft ?: it.editorPrefill,
                    )
                }
            }
            if (snapshot?.started == true) {
                startStream(gateway, sessionId, skipReplay = !restored || transcript.eventId == null)
            } else if (restored && transcript.messages.length() == 0) {
                notice("This session is stopped. Send a prompt to resume it.")
            }
        } else if (snapshot?.started == true) {
            startStream(gateway, sessionId)
        }
        refreshCapabilities(gateway, sessionId, announce = false)
    }

    /** The gateway dropped events for a slow reader; replace the transcript and resume from its cursor. */
    private fun reloadAfterResync() {
        val gateway = client ?: return
        val sessionId = GatewaySessionStore.sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val transcript = runCatching { gateway.messages(sessionId) }.getOrNull() ?: return@launch
            if (GatewaySessionStore.sessionId != sessionId) return@launch
            restoreMessages(transcript.messages)
            GatewaySessionStore.lastEventId = transcript.eventId
            GatewaySessionStore.epoch = transcript.epoch ?: GatewaySessionStore.epoch
        }
    }

    private fun restoreMessages(messages: JSONArray) {
        val restored = SessionTranscript.restore(messages)
        toolMessages.clear()
        toolMessages.putAll(restored.toolMessageIds)
        activeAssistantId = restored.trailingAssistantId
        _state.update { it.copy(messages = restored.messages) }
    }

    /** Screen answers are transient: they never enter `messages`, the transcript, or the state file. */
    private fun handleScreenEvent(event: GatewayEvent) {
        val payload = event.payload
        when (payload.optString("type")) {
            "message_start" -> {
                val message = payload.optJSONObject("message") ?: return
                if (message.optString("role") != "assistant") return
                val id = message.optString("id").ifBlank { UUID.randomUUID().toString() }
                screenAssistantId = id
                _state.update {
                    it.copy(
                        screenMessages = it.screenMessages + ChatMessage(
                            id = id,
                            role = ChatRole.Assistant,
                            text = SessionTranscript.messageText(message),
                        ),
                    )
                }
            }
            "message_update" -> {
                val update = payload.optJSONObject("assistantMessageEvent") ?: return
                when (update.optString("type")) {
                    "text_delta" -> appendScreenText(update.optString("delta"))
                    "text_end" -> replaceScreenText(update.optString("content"))
                    "thinking_delta" -> appendScreenThinking(update.optString("delta"))
                    "thinking_end" -> replaceScreenThinking(update.optString("content"))
                }
            }
            "message_end" -> {
                val message = payload.optJSONObject("message") ?: return
                if (message.optString("role") != "assistant") return
                val id = screenAssistantId
                if (id != null) {
                    finalizeScreenAssistant(
                        id,
                        SessionTranscript.messageText(message),
                        SessionTranscript.messageThinking(message),
                    )
                }
                screenAssistantId = null
            }
        }
    }

    private fun appendScreenText(delta: String) {
        if (delta.isEmpty()) return
        ensureScreenAssistant()
        updateScreenAssistant { it.copy(text = it.text + delta) }
    }

    private fun appendScreenThinking(delta: String) {
        if (delta.isEmpty()) return
        ensureScreenAssistant()
        updateScreenAssistant { it.copy(thinking = it.thinking + delta) }
    }

    private fun replaceScreenText(content: String) {
        if (content.isEmpty()) return
        ensureScreenAssistant()
        updateScreenAssistant { it.copy(text = content) }
    }

    private fun replaceScreenThinking(content: String) {
        if (content.isEmpty()) return
        ensureScreenAssistant()
        updateScreenAssistant { it.copy(thinking = content) }
    }

    private fun finalizeScreenAssistant(id: String, text: String, thinking: String) {
        _state.update { state ->
            state.copy(
                screenMessages = state.screenMessages.map {
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

    private fun ensureScreenAssistant() {
        if (screenAssistantId != null) return
        val id = UUID.randomUUID().toString()
        screenAssistantId = id
        _state.update {
            it.copy(
                screenMessages = it.screenMessages + ChatMessage(
                    id = id,
                    role = ChatRole.Assistant,
                    text = "",
                ),
            )
        }
    }

    private fun updateScreenAssistant(transform: (ChatMessage) -> ChatMessage) {
        val id = screenAssistantId ?: return
        _state.update { state ->
            state.copy(
                screenMessages = state.screenMessages.map {
                    if (it.id == id) transform(it) else it
                },
            )
        }
    }

    private fun handleEvent(event: GatewayEvent) {
        val payload = event.payload
        when (payload.optString("type")) {
            "resync" -> reloadAfterResync()
            "agent_start" -> _state.update { it.copy(streaming = true) }
            "agent_settled" -> {
                activeAssistantId = null
                pendingUserEchoes.clear()
                settings.clearPendingSubmit()
                _state.update { it.copy(streaming = false, stopping = false) }
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
                            addUserMessage(UserMessageText.clean(SessionTranscript.messageText(message)))
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
                                    text = SessionTranscript.messageText(message),
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
                        finalizeAssistant(
                            id,
                            SessionTranscript.messageText(message),
                            SessionTranscript.messageThinking(message),
                        )
                    }
                    activeAssistantId = null
                }
            }
            "tool_execution_start" -> {
                val callId = payload.optString("toolCallId")
                // A restored transcript already has this card; replayed starts must not duplicate it.
                if (toolMessages.containsKey(callId)) return
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
                                args = SessionTranscript.clip(
                                    payload.optJSONObject("args")?.toString(2).orEmpty(),
                                ),
                            ),
                        ),
                    )
                }
            }
            "tool_execution_update" -> {
                val id = toolMessages[payload.optString("toolCallId")] ?: return
                val result = SessionTranscript.toolResultText(payload.optJSONObject("partialResult"))
                _state.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            val tool = message.tool
                            if (message.id == id && tool != null) {
                                message.copy(
                                    tool = tool.copy(
                                        result = SessionTranscript.clip(result),
                                    ),
                                )
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
                val result = SessionTranscript.clip(
                    SessionTranscript.toolResultText(payload.optJSONObject("result")),
                )
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

    /** Returns the new message id, or null when the echo was already present. */
    private fun addUserMessage(text: String): String? {
        if (text.isBlank()) return null
        var added: String? = null
        _state.update { state ->
            val last = state.messages.lastOrNull()
            if (last?.role == ChatRole.User && last.text == text) {
                state
            } else {
                val id = UUID.randomUUID().toString()
                added = id
                state.copy(
                    messages = state.messages + ChatMessage(
                        id = id,
                        role = ChatRole.User,
                        text = text,
                    ),
                )
            }
        }
        return added
    }
}

/** Used when the gateway predates the thinking-level endpoint; Pi still validates the chosen level. */
private val FALLBACK_THINKING_LEVELS = listOf("off", "minimal", "low", "medium", "high")
