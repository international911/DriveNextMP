// Читаем локальные настройки Supabase из файла, который не попадает в Git.
import java.util.Properties

// Android Gradle Plugin собирает приложение в APK.
plugins {
    id("com.android.application")
}

// Получаем URL проекта и публичный ключ без записи их в исходники.
val localSettings = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Экранируем строку для безопасной вставки в сгенерированный BuildConfig.
fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Версии SDK, имя пакета и общие параметры приложения.
android {
    namespace = "com.example.drivenext"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.drivenext"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Эти значения затем доступны в Kotlin как BuildConfig.SUPABASE_URL и ключ.
        buildConfigField("String", "SUPABASE_URL", quoted(localSettings.getProperty("supabase.url", "")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quoted(localSettings.getProperty("supabase.publishableKey", "")))
    }

    buildFeatures {
        buildConfig = true
    }

    // Kotlin и Java компилируются для Java 17.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Библиотеки AndroidX для экранов, заставки, фрагментов и ViewModel.
dependencies {
    // MaterialDatePicker для выбора даты рождения и выдачи прав.
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata:2.6.2")
}
