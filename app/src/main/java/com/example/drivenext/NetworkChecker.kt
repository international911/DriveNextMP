package com.example.drivenext

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// Проверяет именно подтверждённый выход в интернет, а не только наличие Wi-Fi.
object NetworkChecker {
    fun hasInternet(context: Context): Boolean {
        // Получаем системную службу сетевых подключений.
        val manager = context.getSystemService(ConnectivityManager::class.java)
        // Без активной сети и её характеристик доступ считать нельзя.
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        // INTERNET означает возможность передачи данных, VALIDATED — проверку системой.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
