package com.example.drivenext

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

// Все локальные проверки формы собраны здесь, чтобы UI и серверный код не дублировали правила.
object AuthValidator {
    // Почта должна иметь допустимую форму и заканчиваться на .com либо .ru.
    private val emailPattern = Regex("^[A-Z0-9](?:[A-Z0-9._%+-]*[A-Z0-9])?@[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?)*\\.(COM|RU)$", RegexOption.IGNORE_CASE)
    // Форматы дат взяты из требований; строгий разбор отсекает невозможные даты.
    private val birthFormatter = DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT)
    private val issueFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT)

    // Проверяем синтаксис адреса и запрещаем точки по краям/подряд в локальной части.
    fun validEmail(email: String): Boolean {
        val address = email.trim()
        val local = address.substringBefore('@')
        return emailPattern.matches(address) && !local.startsWith('.') && !local.endsWith('.') && ".." !in address
    }

    // Supabase отвергает типовые демонстрационные домены при регистрации.
    fun isTestEmailDomain(email: String): Boolean = email.trim().substringAfterLast('@', "").lowercase(Locale.ROOT) in
        setOf("example.com", "example.net", "example.org", "test.com")

    // Для Gmail проверяем минимальную длину имени до @.
    fun isShortGmailAddress(email: String): Boolean {
        val address = email.trim()
        return address.substringAfterLast('@', "").equals("gmail.com", ignoreCase = true) &&
            address.substringBefore('@').length < 6
    }

    // Дата рождения и дата выдачи используют один контроль прошлого, но разные форматы.
    fun validBirthDate(value: String): Boolean = validPastDate(value, birthFormatter)
    fun validIssueDate(value: String): Boolean = validPastDate(value, issueFormatter)
    // Номер прав: ровно десять обычных цифр без пробелов и букв.
    fun validLicenseNumber(value: String): Boolean = value.length == 10 && value.all { it in '0'..'9' }

    // Возвращаем первую ошибку первого шага; пустой результат означает, что всё верно.
    fun step1Error(draft: RegistrationDraft): ValidationError? = when {
        !validEmail(draft.email) -> ValidationError.EMAIL
        isShortGmailAddress(draft.email) -> ValidationError.EMAIL_GMAIL_LENGTH
        isTestEmailDomain(draft.email) -> ValidationError.EMAIL_TEST_DOMAIN
        draft.password.isBlank() -> ValidationError.PASSWORD_REQUIRED
        draft.password.length < 6 -> ValidationError.PASSWORD_LENGTH
        draft.repeatedPassword.isBlank() -> ValidationError.PASSWORD_REPEAT_REQUIRED
        draft.password != draft.repeatedPassword -> ValidationError.PASSWORD_MATCH
        !draft.acceptedTerms -> ValidationError.TERMS
        else -> null
    }

    // Проверяем обязательные ФИО, дату рождения и пол; отчество можно оставить пустым.
    fun step2Error(draft: RegistrationDraft): ValidationError? = when {
        draft.lastName.isBlank() || draft.firstName.isBlank() || draft.birthDate.isBlank() || draft.gender == null -> ValidationError.REQUIRED_FIELDS
        !validBirthDate(draft.birthDate) -> ValidationError.BIRTH_DATE
        else -> null
    }

    // На третьем шаге обязательны права, дата выдачи и два фото документов.
    fun step3Error(draft: RegistrationDraft): ValidationError? = when {
        draft.licenseNumber.isBlank() || draft.licenseIssueDate.isBlank() -> ValidationError.REQUIRED_FIELDS
        !validLicenseNumber(draft.licenseNumber) -> ValidationError.LICENSE_NUMBER
        !validIssueDate(draft.licenseIssueDate) -> ValidationError.ISSUE_DATE
        draft.licensePhotoUri == null || draft.passportPhotoUri == null -> ValidationError.PHOTOS
        else -> null
    }

    // Разбираем дату строго; будущая дата и год раньше 1900 считаются ошибкой.
    private fun validPastDate(value: String, formatter: DateTimeFormatter): Boolean =
        runCatching { LocalDate.parse(value, formatter) }
            .getOrNull()?.let { !it.isAfter(LocalDate.now()) && it.year >= 1900 } == true
}
