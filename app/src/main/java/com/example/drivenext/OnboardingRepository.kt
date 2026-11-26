package com.example.drivenext

import android.content.Context

// Хранит ответ на вопрос: пользователь уже прошёл приветственные экраны или нет.
class OnboardingRepository(context: Context) {
    // SharedPreferences — небольшое постоянное хранилище самого приложения.
    private val preferences = context.applicationContext.getSharedPreferences("drive_next", Context.MODE_PRIVATE)

    // Если записи ещё нет, считаем, что приветствие не пройдено.
    fun isFinished(): Boolean = preferences.getBoolean("onboarding_finished", false)

    // Запоминаем завершение приветствия даже после закрытия приложения.
    fun markFinished() {
        preferences.edit().putBoolean("onboarding_finished", true).apply()
    }
}
