package com.example.drivenext

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

// Экраны входа и регистрации, которые переключает одна Activity.
enum class AuthPage { LOGIN, STEP1, STEP2, STEP3, SUCCESS }
enum class AuthEventType { HOME, GOOGLE_URL, REGISTERED, RESET, ERROR }

// Разовое событие для UI: переход, сообщение об ошибке либо ссылка OAuth.
data class AuthEvent(val type: AuthEventType, val message: String = "", val uri: Uri? = null) {
    private var handled = false
    // После поворота экрана уже показанное сообщение не показывается второй раз.
    fun consume(): Boolean = if (handled) false else { handled = true; true }
}

// Хранит введённые данные и выполняет сетевые операции вне главного потока.
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    val draft = RegistrationDraft()
    var page = AuthPage.LOGIN
    var loginEmail = ""
    var loginPassword = ""
    var pendingPhoto: PhotoKind? = null
    var cameraUri: Uri? = null
    @Volatile var busy = false
        private set

    // Репозиторий общается с Supabase, executor выполняет запросы последовательно.
    private val repository = AuthRepository(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val mutableEvent = MutableLiveData<AuthEvent>()
    val event: LiveData<AuthEvent> = mutableEvent

    // Успешный вход ведёт на главный экран; успешная регистрация — на экран поздравления.
    fun login() = runAction(AuthEventType.HOME) { repository.signIn(loginEmail, loginPassword) }
    fun register() = runAction(AuthEventType.REGISTERED) { repository.register(draft) }
    fun resetPassword() = runAction(AuthEventType.RESET) { repository.sendPasswordReset(loginEmail) }
    // Обрабатываем обратную ссылку от браузера и меняем код на сессию.
    fun googleCallback(uri: Uri) = runAction(AuthEventType.HOME) { repository.completeGoogleOAuth(uri) }
    // Запрашиваем адрес Google OAuth в фоне и отдаём его Activity для открытия браузера.
    fun startGoogle() {
        if (busy) return
        busy = true
        executor.execute {
            val result = runCatching { repository.startGoogleOAuth() }
            busy = false
            mutableEvent.postValue(result.fold(
                onSuccess = { AuthEvent(AuthEventType.GOOGLE_URL, uri = it) },
                onFailure = { AuthEvent(AuthEventType.ERROR, it.message ?: "Неизвестная ошибка") },
            ))
        }
    }

    // Общая обёртка: блокируем повторный запрос, ловим ошибку и отправляем событие UI.
    private fun runAction(successType: AuthEventType, operation: () -> Unit) {
        if (busy) return
        busy = true
        executor.execute {
            val result = runCatching { operation() }
            busy = false
            mutableEvent.postValue(if (result.isSuccess) AuthEvent(successType) else AuthEvent(AuthEventType.ERROR, result.exceptionOrNull()?.message ?: "Неизвестная ошибка"))
        }
    }

    // Останавливаем фоновый поток, когда ViewModel больше не нужна.
    override fun onCleared() {
        executor.shutdownNow()
        super.onCleared()
    }
}
