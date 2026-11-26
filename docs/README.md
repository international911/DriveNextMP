# DriveNext

Нативное Android-приложение на Kotlin. Сейчас доступны приветствие, вход и регистрация, каталог автомобилей, поиск, настройки и профиль.

## Где находится логика

- `MainActivity.kt`, `StartupViewModel.kt` — запуск, проверка сети и приветствие.
- `AuthActivity.kt`, `AuthForm.kt`, `AuthMedia.kt`, `AuthValidator.kt` — экраны и проверки входа и регистрации.
- `AuthViewModel.kt`, `AuthRepository.kt`, `AuthHttpClient.kt`, `SecureSessionStore.kt` — фоновые запросы, Supabase и защищённая сессия.
- `CatalogActivity.kt`, `CarAdapter.kt`, `CatalogPanels.kt`, `CarRepository.kt` — список машин, поиск, настройки и профиль.
- `SectionActivity.kt` — переход к экрану выбранного автомобиля и другим разделам.
- `app/src/main/res/layout` — XML-разметка экранов; `res/drawable` — изображения и оформление.

## Данные и настройка

Создайте Supabase-проект, выполните `supabase/setup.sql` и `supabase/catalog.sql`. URL и publishable key задаются только в `local.properties`. Схема `supabase/rental_and_vehicle_schema.sql` сохранена для будущих возможностей и текущим клиентом не используется.

Подробнее: [вход и регистрация](AUTH_GUIDE.md), [каталог и поиск](CATALOG_GUIDE.md).
