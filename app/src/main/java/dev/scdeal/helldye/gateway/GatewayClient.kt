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
)

data class GatewayImage(
    val mimeType: String,
    val base64: String,
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

class GatewayException(message: String) : Exception(message)

class GatewayClient(private val config: GatewayConfig) {
    private val apiBase = config.url.trim().trimEnd('/').let { base ->
        if (base.endsWith("/v1")) base else "$base/v1"
    }

    fun info(): JSONObject = request("GET", "/info")

    fun createSession(): GatewaySession {
        val body = JSONObject()
        if (config.cwd.isNotBlank()) body.put("cwd", config.cwd)
        val response = request("POST", "/sessions", body)
        return GatewaySession(
            id = response.getString("id"),
            name = response.optString("name"),
            cwd = response.optString("cwd"),
            status = response.optString("status"),
        )
    }

    fun prompt(
        sessionId: String,
        message: String,
        context: String?,
        images: List<GatewayImage>,
    ) {
        val body = JSONObject().put("message", message)
        if (!context.isNullOrBlank()) body.put("context", context)
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

    fun deleteSession(sessionId: String) {
        request("DELETE", "/sessions/$sessionId")
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

    fun currentModel(sessionId: String): GatewayModel? {
        val response = request("GET", "/sessions/$sessionId/state")
        return response.optJSONObject("model")?.let(::model)
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

    fun events(sessionId: String, lastEventId: String?): Flow<GatewayEvent> = callbackFlow {
        val connection = openConnection("/sessions/$sessionId/events", "GET").apply {
            readTimeout = 0
            setRequestProperty("Accept", "text/event-stream")
            if (!lastEventId.isNullOrBlank()) setRequestProperty("Last-Event-ID", lastEventId)
        }

        val reader = Thread {
            try {
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
        return if (text.isBlank()) JSONObject() else JSONObject(text)
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
