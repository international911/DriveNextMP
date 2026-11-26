package com.example.drivenext

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// Возможные экраны приложения; ViewModel хранит один текущий экран.
enum class Screen { SPLASH, OFFLINE, ONBOARDING, ENTRY, HOME }

// Состояние стартового сценария переживает поворот экрана вместе с ViewModel.
class StartupViewModel(private val onboardingRepository: OnboardingRepository) : ViewModel() {
    // Изменять экран снаружи нельзя: переходы выполняются методами ниже.
    var screen: Screen = Screen.SPLASH
        private set
    // Номер текущего слайда, чтобы не терять его при повороте.
    var onboardingPage: Int = 0
    // Флаг берётся из постоянного хранилища при создании модели.
    var onboardingFinished: Boolean = onboardingRepository.isFinished()
        private set

    // Время запуска по монотонным часам: смена системного времени не повлияет.
    private val splashStartedAt = SystemClock.elapsedRealtime()
    // Сюда запоминаем экран, с которого ушли на экран отсутствия сети.
    private var interruptedScreen: Screen? = null

    // Возвращаем оставшуюся часть 2,5 секунд, но не отрицательное значение.
    fun remainingSplashMillis(): Long = (2500L - (SystemClock.elapsedRealtime() - splashStartedAt)).coerceAtLeast(0L)

    // По окончании заставки выбираем приветствие или вход; без сети показываем ошибку.
    fun finishSplash(hasInternet: Boolean) {
        if (screen != Screen.SPLASH) return
        val destination = if (onboardingFinished) Screen.ENTRY else Screen.ONBOARDING
        screen = if (hasInternet) destination else Screen.OFFLINE
        if (!hasInternet) interruptedScreen = destination
    }

    // При потере сети сохраняем текущий экран, при восстановлении возвращаемся к нему.
    fun onNetworkChanged(hasInternet: Boolean) {
        if (screen == Screen.SPLASH) return
        if (!hasInternet && screen != Screen.OFFLINE) {
            interruptedScreen = screen
            screen = Screen.OFFLINE
        } else if (hasInternet && screen == Screen.OFFLINE) {
            screen = interruptedScreen ?: if (onboardingFinished) Screen.ENTRY else Screen.ONBOARDING
            interruptedScreen = null
        }
    }

    // Завершение приветствия сохраняется, поэтому при следующем запуске оно не повторится.
    fun completeOnboarding() {
        onboardingRepository.markFinished()
        onboardingFinished = true
        screen = Screen.ENTRY
        interruptedScreen = null
    }

    // Переходим на главный экран после успешной авторизации.
    fun openHome() {
        screen = Screen.HOME
        interruptedScreen = null
    }

    // Factory передаёт репозиторий в конструктор ViewModel.
    class Factory(private val repository: OnboardingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Проверяем, что фабрику не попросили создать другую модель.
            require(modelClass == StartupViewModel::class.java)
            return StartupViewModel(repository) as T
        }
    }
}
