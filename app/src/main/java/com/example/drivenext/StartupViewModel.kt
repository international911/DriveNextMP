package com.example.drivenext

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

enum class Screen { SPLASH, OFFLINE, ONBOARDING, ENTRY }

class StartupViewModel(private val onboardingRepository: OnboardingRepository) : ViewModel() {
    var screen: Screen = Screen.SPLASH
        private set
    var onboardingPage: Int = 0
    var onboardingFinished: Boolean = onboardingRepository.isFinished()
        private set

    private val splashStartedAt = SystemClock.elapsedRealtime()
    private var interruptedScreen: Screen? = null

    fun remainingSplashMillis(): Long = (2500L - (SystemClock.elapsedRealtime() - splashStartedAt)).coerceAtLeast(0L)

    fun finishSplash(hasInternet: Boolean) {
        if (screen != Screen.SPLASH) return
        val destination = if (onboardingFinished) Screen.ENTRY else Screen.ONBOARDING
        screen = if (hasInternet) destination else Screen.OFFLINE
        if (!hasInternet) interruptedScreen = destination
    }

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

    fun completeOnboarding() {
        onboardingRepository.markFinished()
        onboardingFinished = true
        screen = Screen.ENTRY
        interruptedScreen = null
    }

    class Factory(private val repository: OnboardingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == StartupViewModel::class.java)
            return StartupViewModel(repository) as T
        }
    }
}
