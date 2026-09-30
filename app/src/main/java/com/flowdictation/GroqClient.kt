package com.flowdictation

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONObject

class GroqException(message: String, val code: Int = 0) : Exception(message)

/** Minimal Groq API client using only the Android/Java standard library. */
object GroqClient {
    private const val BASE = "https://api.groq.com/openai/v1"
    private const val USER_AGENT = "FlowDictation/1.0"

    // ---------- Speech to text ----------

    fun transcribe(key: String, model: String, wav: ByteArray, language: String, vocabulary: String): String {
        val boundary = "----FlowBoundary" + System.currentTimeMillis()
        val body = ByteArrayOutputStream(wav.size + 1024)
        fun field(name: String, value: String) {
            body.write(
                ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" +
                    value + "\r\n").toByteArray(Charsets.UTF_8)
            )
        }
        field("model", model)
        field("response_format", "json")
        field("temperature", "0")
        if (language.isNotBlank()) field("language", language.trim())
        if (vocabulary.isNotBlank()) field("prompt", "Names and terms that may appear: " + vocabulary.trim().take(400) + ".")
        body.write(
            ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n" +
                "Content-Type: audio/wav\r\n\r\n").toByteArray(Charsets.UTF_8)
        )
        body.write(wav)
        body.write(("\r\n--" + boundary + "--\r\n").toByteArray(Charsets.UTF_8))
        val payload = body.toByteArray()

        val conn = URL("$BASE/audio/transcriptions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }
            val text = readResponse(conn)
            return JSONObject(text).optString("text", "").trim()
        } finally {
            conn.disconnect()
        }
    }

    // ---------- Chat (used for cleanup) ----------

    fun chat(key: String, model: String, system: String, user: String, timeoutMs: Int, maxTokens: Int): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        messages.put(JSONObject().put("role", "user").put("content", user))
        val json = JSONObject()
        json.put("model", model)
        json.put("messages", messages)
        json.put("temperature", 0.2)
        json.put("max_completion_tokens", maxTokens)
        json.put("stream", false)
        // gpt-oss models "think" first; keep that short or they burn the token budget and answer slowly.
        if (model.contains("gpt-oss")) json.put("reasoning_effort", "low")
        val payload = json.toString().toByteArray(Charsets.UTF_8)

        val conn = URL("$BASE/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = Math.min(timeoutMs, 5000)
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }
            val text = readResponse(conn)
            val choices = JSONObject(text).optJSONArray("choices")
            val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
            if (content.isBlank()) throw GroqException("Cleanup model returned nothing")
            return content
        } finally {
            conn.disconnect()
        }
    }

    // ---------- Model list (used by the "Test connection" button) ----------

    fun listModels(key: String): List<String> {
        val conn = URL("$BASE/models").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val text = readResponse(conn)
            val data = JSONObject(text).optJSONArray("data") ?: return emptyList()
            val ids = ArrayList<String>()
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id", "") ?: ""
                if (id.isNotEmpty()) ids.add(id)
            }
            return ids
        } finally {
            conn.disconnect()
        }
    }

    // ---------- Shared ----------

    private fun readResponse(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        if (code !in 200..299) throw GroqException(errorMessage(code, text), code)
        return text
    }

    private fun errorMessage(code: Int, body: String): String {
        val api = try {
            JSONObject(body).getJSONObject("error").optString("message", "")
        } catch (e: Exception) {
            ""
        }
        return when (code) {
            401 -> "Groq rejected the API key (401). Check it in the app."
            403 -> "Groq blocked the request (403). " + api.take(120)
            413 -> "Recording too large for Groq (413)."
            429 -> "Groq rate limit reached (429). " + api.take(140)
            else -> "Groq error $code" + (if (api.isNotBlank()) ": " + api.take(140) else "")
        }
    }

    /** Short human-friendly description of any exception from the calls above. */
    fun friendly(e: Exception): String = when (e) {
        is GroqException -> e.message ?: "Groq error"
        is SocketTimeoutException -> "timed out"
        is UnknownHostException -> "no internet connection"
        else -> e.javaClass.simpleName
    }
}
