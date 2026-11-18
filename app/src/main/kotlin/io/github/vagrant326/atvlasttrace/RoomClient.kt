package io.github.vagrant326.atvlasttrace

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HostSession(val code: String, val json: String)

/** Does what the game's "Uruchom wspólny ekran" button does: POST /api/rooms. Blocking. */
object RoomClient {
    private val roomCode = Regex("^[0-9A-F]{6}$")

    fun createRoom(baseUrl: String, language: String): HostSession {
        val connection = URL("$baseUrl/api/rooms").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("language", language).toString().toByteArray())
            }
            if (connection.responseCode !in 200..299) {
                throw IOException("POST /api/rooms returned ${connection.responseCode}")
            }
            val session = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })

            // The page uppercases the code from the URL and only restores a stored session
            // whose code matches it exactly.
            val code = session.optString("code").uppercase()
            if (session.optString("role") != "host" || !roomCode.matches(code) || session.optString("token").isEmpty()) {
                throw IOException("Unexpected /api/rooms response")
            }
            session.put("code", code)
            return HostSession(code, session.toString())
        } finally {
            connection.disconnect()
        }
    }
}
