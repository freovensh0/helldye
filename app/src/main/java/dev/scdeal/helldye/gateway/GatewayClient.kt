package dev.scdeal.helldye.gateway

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class GatewaySession(
    val id: String,
    val name: String,
    val cwd: String,
    val status: String,
    val created: Long = 0,
)

data class GatewayImage(
    val mimeType: String,
    val base64: String,
)

/** Structured file context. The gateway composes the data envelope; the app never pre-wraps it. */
data class GatewayContextFile(
    val name: String,
    val content: String,
)

data class GatewayEvent(
    val id: String?,
    val kind: String,
    val payload: JSONObject,
)

data class GatewayModel(
    val provider: String,
    val id: String,
    val name: String,
    val reasoning: Boolean,
    val contextWindow: Int,
)

data class GatewayCommand(
    val name: String,
    val description: String,
    val source: String,
)

data class GatewayStats(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val totalTokens: Long = 0,
    val cost: Double = 0.0,
    val contextTokens: Long = 0,
    val contextWindow: Long = 0,
    val contextPercent: Int = -1,
)

data class GatewayState(
    val model: GatewayModel?,
    val thinkingLevel: String?,
)

/** Pi transcript plus the event cursor it reflects, so the client can resume the stream without duplicates. */
data class GatewayTranscript(
    val messages: JSONArray,
    val eventId: String?,
)

class GatewayException(message: String) : Exception(message)

class GatewayClient(private val config: GatewayConfig) {
    private val apiBase = config.url.trim().trimEnd('/').let { base ->
        if (base.endsWith("/v1")) base else "$base/v1"
    }

    fun info(): JSONObject = request("GET", "/info")

    fun createSession(cwd: String? = null, ephemeral: Boolean = false): GatewaySession {
        val body = JSONObject()
        val directory = cwd ?: config.cwd
        if (directory.isNotBlank()) body.put("cwd", directory)
        if (ephemeral) body.put("ephemeral", true)
        val response = request("POST", "/sessions", body)
        return GatewaySession(
            id = response.getString("id"),
            name = response.optString("name"),
            cwd = response.optString("cwd"),
            status = response.optString("status"),
            created = response.optLong("created"),
        )
    }

    fun prompt(
        sessionId: String,
        message: String,
        contextFiles: List<GatewayContextFile>,
        images: List<GatewayImage>,
    ) {
        val body = JSONObject().put("message", message)
        if (contextFiles.isNotEmpty()) {
            body.put(
                "context",
                JSONObject().put(
                    "files",
                    JSONArray().apply {
                        contextFiles.forEach { file ->
                            put(
                                JSONObject()
                                    .put("name", file.name)
                                    .put("content", file.content),
                            )
                        }
                    },
                ),
            )
        }
        if (images.isNotEmpty()) {
            body.put(
                "images",
                JSONArray().apply {
                    images.forEach { image ->
                        put(
                            JSONObject()
                                .put("type", "image")
                                .put("data", image.base64)
                                .put("mimeType", image.mimeType),
                        )
                    }
                },
            )
        }
        request("POST", "/sessions/$sessionId/prompt", body)
    }

    fun abort(sessionId: String) {
        request("POST", "/sessions/$sessionId/abort", JSONObject())
    }

    fun compact(sessionId: String) {
        request("POST", "/sessions/$sessionId/compact", JSONObject())
    }

    fun deleteSession(sessionId: String) {
        request("DELETE", "/sessions/$sessionId")
    }

    fun sessions(): List<GatewaySession> {
        val array = requestArray("GET", "/sessions")
        return buildList {
            for (index in 0 until array.length()) {
                val session = array.optJSONObject(index) ?: continue
                add(
                    GatewaySession(
                        id = session.optString("id"),
                        name = session.optString("name"),
                        cwd = session.optString("cwd"),
                        status = session.optString("status"),
                        created = session.optLong("created"),
                    ),
                )
            }
        }
    }

    fun session(sessionId: String): GatewaySession {
        val response = request("GET", "/sessions/$sessionId")
        return GatewaySession(
            id = response.optString("id", sessionId),
            name = response.optString("name"),
            cwd = response.optString("cwd"),
            status = response.optString("status"),
            created = response.optLong("created"),
        )
    }

    fun messages(sessionId: String): GatewayTranscript {
        val response = request("GET", "/sessions/$sessionId/messages")
        return GatewayTranscript(
            messages = response.optJSONArray("messages") ?: JSONArray(),
            eventId = response.optString("eventId").takeIf { it.isNotBlank() },
        )
    }

    fun renameSession(sessionId: String, name: String): GatewaySession {
        val response = request(
            "POST",
            "/sessions/$sessionId/name",
            JSONObject().put("name", name),
        )
        return GatewaySession(
            id = response.optString("id", sessionId),
            name = response.optString("name", name),
            cwd = response.optString("cwd"),
            status = response.optString("status"),
            created = response.optLong("created"),
        )
    }

    fun models(sessionId: String): List<GatewayModel> {
        val response = request("GET", "/sessions/$sessionId/models")
        val array = response.optJSONArray("models") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(model(it)) }
            }
        }
    }

    fun commands(sessionId: String): List<GatewayCommand> {
        val response = request("GET", "/sessions/$sessionId/commands")
        val array = response.optJSONArray("commands") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val command = array.optJSONObject(index) ?: continue
                add(
                    GatewayCommand(
                        name = command.optString("name"),
                        description = command.optString("description"),
                        source = command.optString("source"),
                    ),
                )
            }
        }
    }

    fun state(sessionId: String): GatewayState {
        val response = request("GET", "/sessions/$sessionId/state")
        return GatewayState(
            model = response.optJSONObject("model")?.let(::model),
            thinkingLevel = response.optString("thinkingLevel").takeIf { it.isNotBlank() },
        )
    }

    fun stats(sessionId: String): GatewayStats {
        val response = request("GET", "/sessions/$sessionId/stats")
        val tokens = response.optJSONObject("tokens") ?: JSONObject()
        val context = response.optJSONObject("contextUsage") ?: JSONObject()
        return GatewayStats(
            inputTokens = tokens.optLong("input"),
            outputTokens = tokens.optLong("output"),
            cacheReadTokens = tokens.optLong("cacheRead"),
            cacheWriteTokens = tokens.optLong("cacheWrite"),
            totalTokens = tokens.optLong("total"),
            cost = response.optDouble("cost", 0.0),
            contextTokens = context.optLong("tokens"),
            contextWindow = context.optLong("contextWindow"),
            contextPercent = if (context.isNull("percent")) -1 else context.optInt("percent", -1),
        )
    }

    fun thinkingLevels(sessionId: String): List<String> {
        val response = request("GET", "/sessions/$sessionId/thinking")
        val array = response.optJSONArray("levels") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    fun setThinkingLevel(sessionId: String, level: String) {
        request(
            "POST",
            "/sessions/$sessionId/thinking",
            JSONObject().put("level", level),
        )
    }

    fun extensionResponse(
        sessionId: String,
        id: String,
        value: String? = null,
        confirmed: Boolean? = null,
        cancelled: Boolean = false,
    ) {
        val body = JSONObject().put("id", id)
        value?.let { body.put("value", it) }
        confirmed?.let { body.put("confirmed", it) }
        if (cancelled) body.put("cancelled", true)
        request("POST", "/sessions/$sessionId/extension_ui_response", body)
    }

    fun setModel(sessionId: String, model: GatewayModel) {
        request(
            "POST",
            "/sessions/$sessionId/model",
            JSONObject()
                .put("provider", model.provider)
                .put("modelId", model.id),
        )
    }

    private fun model(json: JSONObject): GatewayModel = GatewayModel(
        provider = json.optString("provider"),
        id = json.optString("id"),
        name = json.optString("name").ifBlank { json.optString("id") },
        reasoning = json.optBoolean("reasoning"),
        contextWindow = json.optInt("contextWindow"),
    )

    fun events(
        sessionId: String,
        lastEventId: String?,
        replay: Boolean = true,
        onConnected: (() -> Unit)? = null,
    ): Flow<GatewayEvent> = callbackFlow {
        val path = "/sessions/$sessionId/events" + if (replay) "" else "?replay=false"
        val connection = openConnection(path, "GET").apply {
            readTimeout = 0
            setRequestProperty("Accept", "text/event-stream")
            if (!lastEventId.isNullOrBlank()) setRequestProperty("Last-Event-ID", lastEventId)
        }

        val reader = Thread {
            try {
                // Wait for the response headers so the caller knows the server subscribed before it
                // sends a prompt that can settle and delete an ephemeral session.
                val code = connection.responseCode
                if (code !in 200..299) throw GatewayException("HTTP $code")
                onConnected?.invoke()
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
                    readEvents(input) { event -> trySend(event) }
                }
                close()
            } catch (error: Exception) {
                close(error)
            }
        }.apply {
            name = "helldye-sse"
            isDaemon = true
            start()
        }

        awaitClose {
            connection.disconnect()
            reader.interrupt()
        }
    }.flowOn(Dispatchers.IO)

    private fun readEvents(reader: BufferedReader, emit: (GatewayEvent) -> Unit) {
        var id: String? = null
        var kind = "message"
        val data = StringBuilder()

        while (true) {
            val line = reader.readLine() ?: break
            when {
                line.startsWith("id:") -> id = line.substringAfter(':').trim()
                line.startsWith("event:") -> kind = line.substringAfter(':').trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substringAfter(':').trimStart())
                }
                line.isBlank() -> {
                    if (data.isNotEmpty()) {
                        val payload = runCatching { JSONObject(data.toString()) }.getOrNull()
                        if (payload != null) emit(GatewayEvent(id, kind, payload))
                    }
                    data.setLength(0)
                    id = null
                    kind = "message"
                }
            }
        }
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val text = requestText(method, path, body)
        return if (text.isBlank() || text == "null") JSONObject() else JSONObject(text)
    }

    private fun requestArray(method: String, path: String, body: JSONObject? = null): JSONArray {
        val text = requestText(method, path, body)
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    private fun requestText(method: String, path: String, body: JSONObject? = null): String {
        val connection = openConnection(path, method)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.reader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            throw GatewayException("HTTP $code: ${text.take(300)}")
        }
        return text
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        if (apiBase.isBlank() || config.url.isBlank()) {
            throw GatewayException("Gateway URL is not configured.")
        }
        val connection = URL(apiBase + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 8_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "HELLDYE/${Build.VERSION.SDK_INT}")
        if (config.token.isNotBlank()) {
            connection.setRequestProperty("Authorization", "Bearer ${config.token}")
        }
        return connection
    }
}
