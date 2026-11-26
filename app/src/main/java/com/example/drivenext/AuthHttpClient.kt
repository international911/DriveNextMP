package com.example.drivenext

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Общий HTTP-клиент Supabase для входа, регистрации и загрузки документов.
class AuthHttpClient(private val appContext: Context, private val baseUrl: String, private val publicKey: String) {
    fun configured(): Boolean = baseUrl.startsWith("https://") && publicKey.isNotBlank()
    // Читаем выбранное изображение по Uri и отправляем в закрытый Storage bucket.
    fun upload(uri: Uri, path: String, session: AuthSession): String {
        val size = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        if (size != null && size > 6L * 1024L * 1024L) throw AuthException("Фотография слишком большая. Выберите файл до 6 МБ.")
        val contentType = appContext.contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw AuthException("Выберите фото в формате JPEG, PNG или WebP.")
        }
        val actualPath = path.removeSuffix(".jpg") + "." + extension
        val encodedPath = actualPath.split('/').joinToString("/") { Uri.encode(it) }
        val connection = open("POST", "/storage/v1/object/registration-documents/$encodedPath", session.accessToken)
        try {
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("x-upsert", "true")
            connection.doOutput = true
            connection.setChunkedStreamingMode(64 * 1024)
            val input = appContext.contentResolver.openInputStream(uri) ?: throw AuthException("Не удалось открыть выбранную фотографию.")
            input.use { source -> connection.outputStream.use { output -> source.copyTo(output) } }
            readResponse(connection)
            return actualPath
        } finally {
            connection.disconnect()
        }
    }

    // Универсальный JSON-запрос: добавляет тело, заголовки и разбирает ответ.
    fun request(method: String, path: String, body: JSONObject? = null, token: String? = null, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        val connection = open(method, path, token)
        try {
            extraHeaders.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    // Создаём соединение с таймаутами и нужным Authorization/apikey.
    private fun open(method: String, path: String, token: String? = null): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("apikey", publicKey)
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }

    // Читаем JSON и переводим известные серверные коды ошибок на русский язык.
    private fun readResponse(connection: HttpURLConnection): JSONObject {
        val code = try { connection.responseCode } catch (error: IOException) {
            throw AuthException("Не удалось подключиться к серверу. Проверьте интернет и повторите попытку.")
        }
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        if (code !in 200..299) {
            val errorCode = json.optString("error_code").ifBlank { json.optString("code") }
            if (errorCode == "email_address_invalid") {
                val email = Regex("Email address \"([^\"]+)\" is invalid")
                    .find(json.optString("msg").ifBlank { json.optString("message") })
                    ?.groupValues?.getOrNull(1).orEmpty()
                val messageId = when {
                    AuthValidator.isShortGmailAddress(email) -> R.string.email_gmail_length
                    AuthValidator.isTestEmailDomain(email) -> R.string.email_test_domain
                    else -> R.string.email_rejected
                }
                throw AuthException(appContext.getString(messageId))
            }
            val knownError = when (errorCode) {
                "invalid_credentials" -> R.string.invalid_credentials
                "email_not_confirmed" -> R.string.email_not_confirmed
                "email_address_not_authorized" -> R.string.email_not_authorized
                "weak_password" -> R.string.password_length
                "user_already_exists", "email_exists" -> R.string.email_already_exists
                "over_email_send_rate_limit", "over_request_rate_limit" -> R.string.rate_limited
                "provider_disabled", "oauth_provider_not_supported" -> R.string.google_provider_disabled
                "flow_state_expired", "flow_state_not_found", "bad_code_verifier" -> R.string.google_retry
                else -> null
            }
            if (knownError != null) throw AuthException(appContext.getString(knownError))
            val message = listOf("error_description", "msg", "message", "error")
                .firstNotNullOfOrNull { json.optString(it).takeIf(String::isNotBlank) }
                ?: "Ошибка сервера (HTTP $code)."
            throw AuthException(message)
        }
        return json
    }

    // Прерываем запрос с понятным сообщением, если Supabase ещё не подключён.
    fun ensureConfigured() {
        if (!configured()) throw AuthException("Сначала настройте Supabase: добавьте URL проекта и публичный ключ в local.properties.")
    }
}
