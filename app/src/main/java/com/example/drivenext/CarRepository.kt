package com.example.drivenext

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Car(val id: String, val brand: String, val model: String, val price: Int, val features: String)
fun Car.photoResource(): Int = when {
    brand.equals("Kia", true) && model.equals("Rio", true) -> R.drawable.car_kia_rio
    brand.equals("Toyota", true) && model.equals("Camry", true) -> R.drawable.car_toyota_camry
    else -> R.drawable.ic_car
}
data class UserProfile(val name: String, val email: String, val avatarPath: String?, val gender: String?, val createdAt: String?)

// Запросы к PostgREST выполняются только с токеном текущего пользователя.
class CarRepository(private val context: Context) {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun cars(query: String = ""): List<Car> {
        val route = if (query.isBlank()) "/rest/v1/cars?select=*&available=eq.true&order=created_at.desc" else {
            val escaped = query.trim().replace("\\", "\\\\").replace("*", "\\*").replace(",", "\\,")
            val filter = Uri.encode("(brand.ilike.*$escaped*,model.ilike.*$escaped*)")
            "/rest/v1/cars?select=*&available=eq.true&or=$filter&order=created_at.desc"
        }
        val array = JSONArray(request(route))
        return (0 until array.length()).map { index -> parseCar(array.getJSONObject(index)) }
    }

    fun car(id: String): Car {
        val rows = JSONArray(request("/rest/v1/cars?select=*&id=eq.$id"))
        return rows.optJSONObject(0)?.let(::parseCar) ?: error("Автомобиль не найден")
    }

    private fun parseCar(row: JSONObject) = Car(row.getString("id"), row.getString("brand"),
        row.getString("model"), row.getInt("price_per_day"), row.optString("features"))

    fun profile(): UserProfile {
        val session = session()
        val rows = JSONArray(request("/rest/v1/profiles?select=first_name,last_name,avatar_path,gender,created_at&id=eq.${session.userId}"))
        val row = rows.optJSONObject(0)
        return UserProfile(listOf(row?.optString("first_name"), row?.optString("last_name"))
            .filterNot { it.isNullOrBlank() }.joinToString(" ").ifBlank { "Пользователь" },
            session.email, row?.optString("avatar_path")?.takeIf { it.isNotBlank() },
            row?.optString("gender"), row?.optString("created_at"))
    }

    fun updateAvatar(uri: Uri) {
        val s = session()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать фотографию")
        require(bytes.size <= 6 * 1024 * 1024) { "Фото должно быть не больше 6 МБ" }
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(mime in listOf("image/jpeg", "image/png", "image/webp")) { "Выберите JPEG, PNG или WebP" }
        val suffix = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "${s.userId}/avatar.$suffix"
        request("/storage/v1/object/registration-documents/$path", "POST", bytes, mime,
            mapOf("x-upsert" to "true"))
        request("/rest/v1/profiles?id=eq.${s.userId}", "PATCH",
            JSONObject().put("avatar_path", path).toString().toByteArray(), "application/json")
    }

    fun avatar(path: String): ByteArray {
        val s = session()
        val connection = URL("$base/storage/v1/object/authenticated/registration-documents/${Uri.encode(path, "/")}").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Authorization", "Bearer ${s.accessToken}")
            if (connection.responseCode !in 200..299) error("Не удалось загрузить аватар")
            return connection.inputStream.use { it.readBytes() }
        } finally { connection.disconnect() }
    }

    private fun session(): AuthSession = SecureSessionStore(context).load()?.takeIf { it.hasValidAccessToken() }
        ?: error("Сессия истекла. Войдите снова.")

    private fun request(route: String, method: String = "GET", body: ByteArray? = null,
                        mime: String = "application/json", headers: Map<String, String> = emptyMap()): String {
        require(base.startsWith("https://") && key.isNotBlank()) { "Настройте Supabase в local.properties" }
        val connection = URL(base + route).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Authorization", "Bearer ${session().accessToken}")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", mime)
                connection.outputStream.use { it.write(body) }
            }
            val success = connection.responseCode in 200..299
            val response = (if (success) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!success) {
                val details = runCatching { JSONObject(response) }.getOrNull()
                val message = if (details?.optString("code") in listOf("PGRST202", "PGRST205"))
                    "Данные пока не настроены. Обратитесь к администратору."
                else details?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Ошибка сервера (${connection.responseCode})"
                error(message)
            }
            return response
        } finally { connection.disconnect() }
    }
}
