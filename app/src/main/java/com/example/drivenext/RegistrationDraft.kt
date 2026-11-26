package com.example.drivenext

import android.net.Uri

// Черновик регистрации: поля остаются заполненными при переходе между шагами.
data class RegistrationDraft(
    // Учётные данные первого шага.
    var email: String = "",
    var password: String = "",
    var repeatedPassword: String = "",
    var acceptedTerms: Boolean = false,
    // Личные сведения второго шага.
    var lastName: String = "",
    var firstName: String = "",
    var middleName: String = "",
    var birthDate: String = "",
    var gender: Gender? = null,
    // Номер, дата и три изображения третьего шага; аватар необязателен.
    var licenseNumber: String = "",
    var licenseIssueDate: String = "",
    var avatarUri: Uri? = null,
    var licensePhotoUri: Uri? = null,
    var passportPhotoUri: Uri? = null,
)

// Значения выбора пола в форме и данные, отправляемые в профиль.
enum class Gender { MALE, FEMALE }
// Помогает понять, для какого поля вернулась фотография из галереи/камеры.
enum class PhotoKind { AVATAR, LICENSE, PASSPORT }
// Коды ошибок валидатора: UI переводит их в понятные сообщения.
enum class ValidationError { EMAIL, EMAIL_GMAIL_LENGTH, EMAIL_TEST_DOMAIN, PASSWORD_REQUIRED, PASSWORD_LENGTH, PASSWORD_REPEAT_REQUIRED, PASSWORD_MATCH, TERMS, REQUIRED_FIELDS, BIRTH_DATE, LICENSE_NUMBER, ISSUE_DATE, PHOTOS }
