plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.drivenext"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.drivenext"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.13.0")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
}
