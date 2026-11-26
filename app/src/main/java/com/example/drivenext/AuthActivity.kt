package com.example.drivenext

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.datepicker.MaterialDatePicker

// Один экран управляет входом и тремя шагами регистрации через AuthPage.
class AuthActivity : FragmentActivity() {
    private lateinit var model: AuthViewModel
    private val form by lazy { AuthForm(this, model) }
    // Системная галерея возвращает Uri выбранной фотографии либо null при отмене.
    private val gallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { media.savePhoto(uri); render() }
    }
    // Камера записывает снимок в заранее подготовленный Uri.
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) model.cameraUri?.let { uri -> media.savePhoto(uri) }
        model.cameraUri = null
        render()
    }
    private val media: AuthMedia by lazy { AuthMedia(this, model, gallery, camera, ::syncFields, ::render) }

    // Создаём разметку, подписываемся на события ViewModel и обрабатываем deep link.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[AuthViewModel::class.java]
        if (savedInstanceState == null && intent?.data == null) {
            model.page = if (intent.getBooleanExtra(EXTRA_REGISTER, false)) AuthPage.STEP1 else AuthPage.LOGIN
        }
        // Событие приходит после сетевой операции: скрываем загрузку и показываем итог.
        model.event.observe(this) { event ->
            if (!event.consume()) return@observe
            findViewById<View>(R.id.loadingOverlay)?.visibility = View.GONE
            when (event.type) {
                AuthEventType.HOME -> openHome()
                AuthEventType.GOOGLE_URL -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, requireNotNull(event.uri))) }
                    .onFailure { showError(getString(R.string.browser_unavailable)) }
                AuthEventType.REGISTERED -> { model.page = AuthPage.SUCCESS; render() }
                AuthEventType.RESET -> AlertDialog.Builder(this).setMessage(R.string.reset_sent).setPositiveButton(R.string.ok, null).show()
                AuthEventType.ERROR -> showError(event.message)
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        render()
        // Android восстанавливает открытый диалог после поворота; слушатель добавляем снова.
        (supportFragmentManager.findFragmentByTag("birthDatePicker") as? MaterialDatePicker<*>)
            ?.let { media.attachDatePickerListener(it, true) }
        (supportFragmentManager.findFragmentByTag("issueDatePicker") as? MaterialDatePicker<*>)
            ?.let { media.attachDatePickerListener(it, false) }
        intent?.data?.let(::handleCallback)
    }

    // Уже открытая Activity тоже должна принять возврат из браузера Google.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(::handleCallback)
    }

    // При повороте сохраняем нужные данные, которыми Android не управляет сам.
    override fun onSaveInstanceState(outState: Bundle) {
        syncFields()
        super.onSaveInstanceState(outState)
    }

    // Принимаем только deep link своего приложения и передаём его в ViewModel.
    private fun handleCallback(uri: Uri) {
        if (uri.scheme == "com.example.drivenext" && uri.host == "auth-callback") {
            findViewById<View>(R.id.loadingOverlay).visibility = View.VISIBLE
            model.googleCallback(uri)
            intent.data = null
        }
    }

    // Скрываем лишние секции, заполняем поля черновиком и подключаем обработчики.
    private fun render() {
        setContentView(R.layout.screen_auth)
        val page = model.page
        val isLogin = page == AuthPage.LOGIN
        val isStep1 = page == AuthPage.STEP1
        findViewById<View>(R.id.credentials).visibility = if (isLogin || isStep1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.repeatSection).visibility = if (isStep1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.forgotPassword).visibility = if (isLogin) View.VISIBLE else View.GONE
        findViewById<View>(R.id.personalSection).visibility = if (page == AuthPage.STEP2) View.VISIBLE else View.GONE
        findViewById<View>(R.id.documentsSection).visibility = if (page == AuthPage.STEP3) View.VISIBLE else View.GONE
        findViewById<View>(R.id.successSection).visibility = if (page == AuthPage.SUCCESS) View.VISIBLE else View.GONE
        findViewById<View>(R.id.googleButton).visibility = if (isLogin) View.VISIBLE else View.GONE
        findViewById<View>(R.id.authSwitch).visibility = if (isLogin) View.VISIBLE else View.GONE
        findViewById<View>(R.id.authBack).visibility = if (isLogin || page == AuthPage.SUCCESS) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.authTitle).setText(when (page) {
            AuthPage.LOGIN -> R.string.login_title
            AuthPage.STEP1 -> R.string.create_account
            AuthPage.STEP2, AuthPage.STEP3 -> R.string.create_account
            AuthPage.SUCCESS -> R.string.success_top
        })
        findViewById<TextView>(R.id.passwordLabel).setText(if (isLogin) R.string.password else R.string.password_create)
        findViewById<TextView>(R.id.authSubtitle).text = when (page) {
            AuthPage.LOGIN -> getString(R.string.login_subtitle)
            AuthPage.STEP1 -> getString(R.string.step_of_three, 1)
            AuthPage.STEP2 -> getString(R.string.step_of_three, 2)
            AuthPage.STEP3 -> getString(R.string.step_of_three, 3)
            AuthPage.SUCCESS -> ""
        }
        val primary = findViewById<Button>(R.id.authPrimary)
        primary.setText(if (isLogin) R.string.login else R.string.next)
        primary.setOnClickListener { primaryAction() }
        findViewById<View>(R.id.authBack).setOnClickListener { goBack() }
        findViewById<View>(R.id.authSwitch).setOnClickListener { syncFields(); model.page = AuthPage.STEP1; render() }
        findViewById<View>(R.id.forgotPassword).setOnClickListener {
            syncFields()
            if (AuthValidator.validEmail(model.loginEmail)) { model.resetPassword(); showLoading() }
            else showError(getString(R.string.reset_email_first))
        }
        findViewById<View>(R.id.googleButton).setOnClickListener {
            model.startGoogle()
            showLoading()
        }
        form.fill()
        findViewById<View>(R.id.birthDateButton).setOnClickListener { media.chooseDate(true) }
        findViewById<View>(R.id.issueDateButton).setOnClickListener { media.chooseDate(false) }
        findViewById<View>(R.id.avatarButton).setOnClickListener { media.choosePhoto(PhotoKind.AVATAR) }
        findViewById<View>(R.id.licensePhotoButton).setOnClickListener { media.choosePhoto(PhotoKind.LICENSE) }
        findViewById<View>(R.id.passportPhotoButton).setOnClickListener { media.choosePhoto(PhotoKind.PASSPORT) }
        form.bindValidation()
        if (model.busy) showLoading()
    }

    private fun syncFields() = form.syncFields()

    // Основная кнопка: проверка текущего шага, переход либо запрос к Supabase.
    private fun primaryAction() {
        syncFields()
        when (model.page) {
            AuthPage.LOGIN -> {
                if (!AuthValidator.validEmail(model.loginEmail)) { showError(getString(R.string.email_invalid)); return }
                model.login(); showLoading()
            }
            AuthPage.STEP1 -> {
                val error = AuthValidator.step1Error(model.draft)
                if (error != null) showError(validationMessage(error)) else { model.page = AuthPage.STEP2; render() }
            }
            AuthPage.STEP2 -> {
                val error = AuthValidator.step2Error(model.draft)
                if (error != null) showError(validationMessage(error)) else { model.page = AuthPage.STEP3; render() }
            }
            AuthPage.STEP3 -> {
                val error = AuthValidator.step3Error(model.draft)
                if (error != null) showError(validationMessage(error)) else { model.register(); showLoading() }
            }
            AuthPage.SUCCESS -> openHome()
        }
    }

    private fun validationMessage(error: ValidationError) = form.validationMessage(error)

    // Возвращаемся на предыдущий шаг, не теряя данные черновика.
    private fun goBack() {
        syncFields()
        when (model.page) {
            AuthPage.LOGIN, AuthPage.STEP1 -> finish()
            AuthPage.STEP2 -> { model.page = AuthPage.STEP1; render() }
            AuthPage.STEP3 -> { model.page = AuthPage.STEP2; render() }
            AuthPage.SUCCESS -> openHome()
        }
    }

    // Пока идёт сеть, перекрываем форму индикатором загрузки.
    private fun showLoading() { findViewById<View>(R.id.loadingOverlay).visibility = View.VISIBLE }
    // Ошибку показываем стандартным диалогом с кнопкой подтверждения.
    private fun showError(message: String) {
        AlertDialog.Builder(this).setTitle(R.string.error_title).setMessage(message).setPositiveButton(R.string.ok, null).show()
    }
    // Закрываем вход и просим MainActivity открыть пустой главный экран.
    private fun openHome() {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_HOME, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    companion object { const val EXTRA_REGISTER = "register" }
}
