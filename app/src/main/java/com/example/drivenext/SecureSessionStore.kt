package com.example.drivenext

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Данные пользователя и срок действия токена доступа после входа.
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtMillis: Long,
) {
    // Запас 30 секунд защищает от использования токена в момент истечения.
    fun hasValidAccessToken(): Boolean = accessToken.isNotBlank() && expiresAtMillis > System.currentTimeMillis() + 30_000L
}

// Сохраняет сессию и временные OAuth-параметры в зашифрованном виде.
class SecureSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("secure_auth", Context.MODE_PRIVATE)

    // Синхронизация не даёт двум потокам одновременно менять сохранённую сессию.
    @Synchronized
    fun save(session: AuthSession) {
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("user_id", session.userId)
            .put("email", session.email)
            .put("expires_at", session.expiresAtMillis)
        write("session", json.toString())
    }

    // Если сохранённые данные повреждены, считаем, что пользователь вышел.
    @Synchronized
    fun load(): AuthSession? = read("session")?.let { serialized ->
        runCatching {
            val json = JSONObject(serialized)
            AuthSession(
                json.getString("access_token"),
                json.getString("refresh_token"),
                json.getString("user_id"),
                json.optString("email"),
                json.getLong("expires_at"),
            )
        }.getOrNull()
    }

    // Одноразовое состояние защищает ответ от подмены, а проверочный код нужен для получения токена.
    @Synchronized
    fun saveOAuth(state: String, verifier: String) {
        write("oauth", JSONObject().put("state", state).put("verifier", verifier).toString())
    }

    // Данные входа через браузер используем один раз и только при совпадении состояния.
    @Synchronized
    fun consumeOAuth(expectedState: String): String? {
        val json = read("oauth")?.let { runCatching { JSONObject(it) }.getOrNull() }
        preferences.edit().remove("oauth").apply()
        return if (json?.optString("state") == expectedState) json.optString("verifier").takeIf(String::isNotBlank) else null
    }

    // При выходе удаляем как сессию, так и незавершённый OAuth-вход.
    @Synchronized
    fun clear() {
        preferences.edit().remove("session").remove("oauth").apply()
    }

    // Шифруем запись и сохраняем её вместе с вектором инициализации в настройках приложения.
    private fun write(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        preferences.edit().putString(key, payload).apply()
    }

    // Расшифровываем запись; если ключ/данные неверны, удаляем испорченное значение.
    private fun read(key: String): String? {
        val payload = preferences.getString(key, null) ?: return null
        return runCatching {
            val parts = payload.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse {
            preferences.edit().remove(key).apply()
            null
        }
    }

    // Берём ключ из защищённого хранилища устройства или создаём при первом запуске.
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object { private const val KEY_ALIAS = "drivenext_auth_v1" }
}
