package com.example.drivenext

import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup

// Привязывает поля входа и регистрации к черновику и управляет доступностью кнопки.
class AuthForm(private val activity: AuthActivity, private val model: AuthViewModel) {
    private fun <T : View> view(id: Int): T = activity.findViewById(id)
    private fun value(id: Int) = view<EditText>(id).text.toString()

    fun fill() {
        val login = model.page == AuthPage.LOGIN
        view<EditText>(R.id.authEmail).setText(if (login) model.loginEmail else model.draft.email)
        view<EditText>(R.id.authPassword).setText(if (login) model.loginPassword else model.draft.password)
        view<EditText>(R.id.authRepeat).setText(model.draft.repeatedPassword)
        view<CheckBox>(R.id.authTerms).isChecked = model.draft.acceptedTerms
        view<EditText>(R.id.authLastName).setText(model.draft.lastName)
        view<EditText>(R.id.authFirstName).setText(model.draft.firstName)
        view<EditText>(R.id.authMiddleName).setText(model.draft.middleName)
        view<EditText>(R.id.licenseNumber).setText(model.draft.licenseNumber)
        view<RadioGroup>(R.id.genderGroup).check(when (model.draft.gender) {
            Gender.MALE -> R.id.genderMale
            Gender.FEMALE -> R.id.genderFemale
            null -> -1
        })
        view<Button>(R.id.birthDateButton).text = model.draft.birthDate.ifBlank { activity.getString(R.string.birth_date_hint) }
        view<Button>(R.id.issueDateButton).text = model.draft.licenseIssueDate.ifBlank { activity.getString(R.string.license_issue_date_hint) }
        view<View>(R.id.avatarButton).contentDescription = activity.getString(
            if (model.draft.avatarUri == null) R.string.profile_photo else R.string.photo_selected,
        )
        view<ImageView>(R.id.avatarImage).apply {
            if (model.draft.avatarUri == null) {
                val padding = (12 * resources.displayMetrics.density + 0.5f).toInt()
                setPadding(padding, padding, padding, padding)
                setImageResource(R.drawable.ic_avatar)
            } else {
                setPadding(0, 0, 0, 0)
                setImageURI(model.draft.avatarUri)
            }
        }
        view<Button>(R.id.licensePhotoButton).setText(if (model.draft.licensePhotoUri == null) R.string.upload_photo else R.string.photo_selected)
        view<Button>(R.id.passportPhotoButton).setText(if (model.draft.passportPhotoUri == null) R.string.upload_photo else R.string.photo_selected)
    }

    fun bindValidation() {
        setupEye(R.id.authPassword, R.id.togglePassword)
        setupEye(R.id.authRepeat, R.id.toggleRepeat)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updatePrimary()
            override fun afterTextChanged(s: Editable?) = Unit
        }
        listOf(R.id.authEmail, R.id.authPassword, R.id.authRepeat, R.id.authLastName, R.id.authFirstName,
            R.id.authMiddleName, R.id.licenseNumber).forEach { view<EditText>(it).addTextChangedListener(watcher) }
        view<CheckBox>(R.id.authTerms).setOnCheckedChangeListener { _, _ -> updatePrimary() }
        view<RadioGroup>(R.id.genderGroup).setOnCheckedChangeListener { _, _ -> updatePrimary() }
        updatePrimary()
    }

    private fun setupEye(fieldId: Int, buttonId: Int) {
        val field = view<EditText>(fieldId)
        view<ImageButton>(buttonId).setOnClickListener { button ->
            val visible = field.transformationMethod !is PasswordTransformationMethod
            field.transformationMethod = if (visible) PasswordTransformationMethod.getInstance() else HideReturnsTransformationMethod.getInstance()
            (button as ImageButton).setImageResource(if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
            button.contentDescription = activity.getString(if (visible) R.string.show_password else R.string.hide_password)
            field.setSelection(field.length())
        }
    }

    fun updatePrimary() {
        val enabled = when (model.page) {
            AuthPage.LOGIN, AuthPage.STEP1 -> value(R.id.authEmail).isNotBlank() && value(R.id.authPassword).isNotBlank()
            AuthPage.STEP2 -> value(R.id.authLastName).isNotBlank() && value(R.id.authFirstName).isNotBlank() &&
                model.draft.birthDate.isNotBlank() && view<RadioGroup>(R.id.genderGroup).checkedRadioButtonId != -1
            AuthPage.STEP3 -> value(R.id.licenseNumber).isNotBlank() && model.draft.licenseIssueDate.isNotBlank() &&
                model.draft.licensePhotoUri != null && model.draft.passportPhotoUri != null
            AuthPage.SUCCESS -> true
        }
        view<Button>(R.id.authPrimary).apply { isEnabled = enabled; alpha = if (enabled) 1f else 0.45f }
        view<View>(R.id.photosError).visibility = if (model.page == AuthPage.STEP3 &&
            value(R.id.licenseNumber).isNotBlank() && model.draft.licenseIssueDate.isNotBlank() &&
            (model.draft.licensePhotoUri == null || model.draft.passportPhotoUri == null)) View.VISIBLE else View.GONE
        val showRequiredError = when (model.page) {
            AuthPage.STEP2 -> {
                val anyEntered = value(R.id.authLastName).isNotBlank() || value(R.id.authFirstName).isNotBlank() ||
                    model.draft.birthDate.isNotBlank() || view<RadioGroup>(R.id.genderGroup).checkedRadioButtonId != -1
                anyEntered && !enabled
            }
            AuthPage.STEP3 -> {
                val anyEntered = value(R.id.licenseNumber).isNotBlank() || model.draft.licenseIssueDate.isNotBlank() ||
                    model.draft.licensePhotoUri != null || model.draft.passportPhotoUri != null
                anyEntered && (value(R.id.licenseNumber).isBlank() || model.draft.licenseIssueDate.isBlank())
            }
            else -> false
        }
        view<View>(R.id.requiredError).visibility = if (showRequiredError) View.VISIBLE else View.GONE
    }

    fun syncFields() {
        when (model.page) {
            AuthPage.LOGIN -> { model.loginEmail = value(R.id.authEmail); model.loginPassword = value(R.id.authPassword) }
            AuthPage.STEP1 -> {
                model.draft.email = value(R.id.authEmail); model.draft.password = value(R.id.authPassword)
                model.draft.repeatedPassword = value(R.id.authRepeat)
                model.draft.acceptedTerms = view<CheckBox>(R.id.authTerms).isChecked
            }
            AuthPage.STEP2 -> {
                model.draft.lastName = value(R.id.authLastName); model.draft.firstName = value(R.id.authFirstName)
                model.draft.middleName = value(R.id.authMiddleName)
                model.draft.gender = when (view<RadioGroup>(R.id.genderGroup).checkedRadioButtonId) {
                    R.id.genderMale -> Gender.MALE
                    R.id.genderFemale -> Gender.FEMALE
                    else -> null
                }
            }
            AuthPage.STEP3 -> model.draft.licenseNumber = value(R.id.licenseNumber)
            AuthPage.SUCCESS -> Unit
        }
    }

    fun validationMessage(error: ValidationError): String = activity.getString(when (error) {
        ValidationError.EMAIL -> R.string.email_invalid
        ValidationError.EMAIL_GMAIL_LENGTH -> R.string.email_gmail_length
        ValidationError.EMAIL_TEST_DOMAIN -> R.string.email_test_domain
        ValidationError.PASSWORD_REQUIRED -> R.string.password_required
        ValidationError.PASSWORD_LENGTH -> R.string.password_length
        ValidationError.PASSWORD_REPEAT_REQUIRED -> R.string.password_repeat_required
        ValidationError.PASSWORD_MATCH -> R.string.password_mismatch
        ValidationError.TERMS -> R.string.terms_required
        ValidationError.REQUIRED_FIELDS -> R.string.required_fields
        ValidationError.BIRTH_DATE -> R.string.birth_invalid
        ValidationError.LICENSE_NUMBER -> R.string.license_invalid
        ValidationError.ISSUE_DATE -> R.string.issue_invalid
        ValidationError.PHOTOS -> R.string.photos_required
    })
}
