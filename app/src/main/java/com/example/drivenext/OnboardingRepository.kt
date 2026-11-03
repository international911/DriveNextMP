package com.example.drivenext

import android.content.Context

class OnboardingRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("drive_next", Context.MODE_PRIVATE)

    fun isFinished(): Boolean = preferences.getBoolean("onboarding_finished", false)

    fun markFinished() {
        preferences.edit().putBoolean("onboarding_finished", true).apply()
    }
}
