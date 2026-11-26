// Источники, из которых Gradle загружает плагины.
pluginManagement {
    repositories {
        maven { url = uri("local-maven") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// Источники обычных библиотек AndroidX и запрет репозиториев внутри модулей.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("local-maven") }
        google()
        mavenCentral()
    }
}
// Имя проекта и подключённый модуль Android-приложения.
rootProject.name = "DriveNextMP"
include(":app")
