package com.example.drivenext

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Ошибка, текст которой можно безопасно показать пользователю.
class AuthException(message: String) : Exception(message)

// Отправляет запросы входа, загрузки файлов и сохранения профиля в Supabase.
class AuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sessions = SecureSessionStore(appContext)
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val publicKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
    private val client = AuthHttpClient(appContext, baseUrl, publicKey)

    // Проверяем, что адрес и публичный ключ были переданы сборке через local.properties.
    fun configured(): Boolean = client.configured()

    // На старте приложения открываем дом только при действующем локальном токене.
    fun validLocalSession(): Boolean = configured() && sessions.load()?.hasValidAccessToken() == true

    // Выход удаляет токены пользователя.
    fun clearSession() = sessions.clear()

    // Обычный вход по почте и паролю; ответ Supabase превращаем в локальную сессию.
    fun signIn(email: String, password: String) {
        client.ensureConfigured()
        val body = JSONObject().put("email", email.trim()).put("password", password)
        saveSession(client.request("POST", "/auth/v1/token?grant_type=password", body = body))
    }

    // Supabase отправляет ссылку восстановления на указанный адрес.
    fun sendPasswordReset(email: String) {
        client.ensureConfigured()
        client.request("POST", "/auth/v1/recover", body = JSONObject().put("email", email.trim()))
    }

    // Создаём аккаунт, загружаем три фото и записываем профиль пользователя.
    fun register(draft: RegistrationDraft) {
        client.ensureConfigured()
        // Если пользователь уже вошёл с той же почтой, повторно создавать аккаунт не нужно.
        val current = sessions.load()
        val session = if (current?.email.equals(draft.email.trim(), ignoreCase = true)) {
            if (current?.hasValidAccessToken() == true) current else refresh(current!!)
        } else {
            val body = JSONObject()
                .put("email", draft.email.trim())
                .put("password", draft.password)
                .put("data", JSONObject().put("first_name", draft.firstName.trim()).put("last_name", draft.lastName.trim()))
            val response = client.request("POST", "/auth/v1/signup", body = body)
            // Пока почта не подтверждена, сервер не выдаёт токен для загрузки файлов.
            if (response.optString("access_token").isBlank()) {
                throw AuthException("Аккаунт создан. Подтвердите электронную почту и войдите, чтобы завершить загрузку документов.")
            }
            saveSession(response)
        }

        val userId = session.userId
        // Путь к каждому файлу начинается с номера владельца для проверки доступа.
        val prefix = "$userId/"
        val avatarPath = draft.avatarUri?.let { client.upload(it, prefix + "avatar.jpg", session) }
        val licensePath = client.upload(draft.licensePhotoUri ?: throw AuthException("Добавьте фото водительского удостоверения."), prefix + "license.jpg", session)
        val passportPath = client.upload(draft.passportPhotoUri ?: throw AuthException("Добавьте фото паспорта."), prefix + "passport.jpg", session)

        // Перед отправкой переводим даты в формат года, месяца и дня, который принимает база.
        val birth = LocalDate.parse(draft.birthDate, DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US)).toString()
        val issued = LocalDate.parse(draft.licenseIssueDate, DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)).toString()
        val profile = JSONObject()
            .put("id", userId)
            .put("last_name", draft.lastName.trim())
            .put("first_name", draft.firstName.trim())
            .put("middle_name", draft.middleName.trim().takeIf(String::isNotEmpty))
            .put("birth_date", birth)
            .put("gender", if (draft.gender == Gender.MALE) "male" else "female")
            .put("license_number", draft.licenseNumber)
            .put("license_issue_date", issued)
            .put("avatar_path", avatarPath)
            .put("license_photo_path", licensePath)
            .put("passport_photo_path", passportPath)
        // При повторной отправке обновляем профиль того же пользователя по его номеру.
        client.request("POST", "/rest/v1/profiles?on_conflict=id", body = profile, token = session.accessToken, extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
    }

        // Перед входом через Google проверяем, включён ли этот способ на сервере.
    fun startGoogleOAuth(): Uri {
        client.ensureConfigured()
        val providers = client.request("GET", "/auth/v1/settings").optJSONObject("external")
        if (providers?.optBoolean("google") != true) throw AuthException(appContext.getString(R.string.google_provider_disabled))
        val random = SecureRandom()
        // Секретная часть остаётся в приложении; на сервер отправляем только её отпечаток.
        val verifierBytes = ByteArray(48).also(random::nextBytes)
        val stateBytes = ByteArray(24).also(random::nextBytes)
        val verifier = Base64.encodeToString(verifierBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val state = Base64.encodeToString(stateBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        // Сохраняем данные проверки до возвращения пользователя из браузера.
        sessions.saveOAuth(state, verifier)
        val callback = Uri.parse("com.example.drivenext://auth-callback").buildUpon().appendQueryParameter("state", state).build()
        return Uri.parse("$baseUrl/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", callback.toString())
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .build()
    }

    // Проверяем ссылку возврата и обмениваем полученный код на токены.
    fun completeGoogleOAuth(callback: Uri) {
        client.ensureConfigured()
        val errorCode = callback.getQueryParameter("error")
        val errorDescription = callback.getQueryParameter("error_description")
        if (!errorCode.isNullOrBlank() || !errorDescription.isNullOrBlank()) {
            val message = when (errorCode) {
                "access_denied" -> appContext.getString(R.string.google_cancelled)
                "provider_disabled", "oauth_provider_not_supported" -> appContext.getString(R.string.google_provider_disabled)
                else -> errorDescription?.takeIf(String::isNotBlank)
                    ?: appContext.getString(R.string.google_retry)
            }
            throw AuthException(message)
        }
        // Отклоняем ответ без состояния, с чужим состоянием или его повтором.
        val state = callback.getQueryParameter("state") ?: throw AuthException("Не удалось проверить ответ Google. Начните вход заново.")
        val verifier = sessions.consumeOAuth(state) ?: throw AuthException("Срок действия входа истёк. Начните вход через Google заново.")
        val code = callback.getQueryParameter("code") ?: throw AuthException("Google не вернул код авторизации.")
        val body = JSONObject().put("auth_code", code).put("code_verifier", verifier)
        saveSession(client.request("POST", "/auth/v1/token?grant_type=pkce", body = body))
    }

    // Продлеваем сессию, когда истёк токен доступа.
    private fun refresh(session: AuthSession): AuthSession {
        if (session.refreshToken.isBlank()) throw AuthException("Сессия истекла. Войдите снова.")
        val body = JSONObject().put("refresh_token", session.refreshToken)
        return saveSession(client.request("POST", "/auth/v1/token?grant_type=refresh_token", body = body))
    }

    // Проверяем ответ сервера и сохраняем токены в защищённом хранилище.
    private fun saveSession(json: JSONObject): AuthSession {
        val user = json.optJSONObject("user") ?: throw AuthException("Сервер не вернул данные пользователя.")
        val session = AuthSession(
            json.optString("access_token"),
            json.optString("refresh_token"),
            user.optString("id"),
            user.optString("email"),
            System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
        )
        if (session.accessToken.isBlank() || session.userId.isBlank()) throw AuthException("Сервер не вернул действительную сессию.")
        sessions.save(session)
        return session
    }

}
