package com.example.drivenext

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.Executors

// Управляет каталогом, поиском, настройками и профилем; данные автомобилей получает с сервера.
class CatalogActivity : FragmentActivity() {
    private enum class Page { HOME, RESULTS, SETTINGS, PROFILE, DETAILS, FAVORITES, SECTION }
    private var page = Page.HOME
    private var query = ""
    private var generation = 0
    private val executor = Executors.newSingleThreadExecutor()
    private val repository by lazy { CarRepository(this) }
    private val panels by lazy { CatalogPanels(this) }
    private lateinit var adapter: CarAdapter
    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) work({ repository.updateAvatar(uri) }, { showProfile() }, { showError(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_catalog)
        query = getPreferences(MODE_PRIVATE).getString("last_query", "").orEmpty()
        adapter = CarAdapter({ car -> showInfo(car) }, { car -> openRental(car) })
        findViewById<RecyclerView>(R.id.carList).apply { layoutManager = LinearLayoutManager(this@CatalogActivity); adapter = this@CatalogActivity.adapter }
        findViewById<Button>(R.id.catalogRetry).setOnClickListener { loadCars() }
        findViewById<TextView>(R.id.catalogBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val searchView = findViewById<SearchView>(R.id.carSearch)
        searchView.setQuery(query, false)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?): Boolean {
                search(text.orEmpty())
                searchView.clearFocus()
                return true
            }
            override fun onQueryTextChange(text: String?): Boolean {
                query = text.orEmpty().trim()
                return true
            }
        })
        findViewById<BottomNavigationView>(R.id.catalogNavigation).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { showHome(); true }
                R.id.nav_saved -> { page = Page.FAVORITES; ++generation; showPanel("Избранное"); panels.text("Сохранённые автомобили появятся здесь."); true }
                R.id.nav_settings -> { showSettings(); true }
                else -> false
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = when (page) {
                Page.PROFILE, Page.SECTION -> showSettings()
                Page.DETAILS -> if (query.isBlank()) showHome() else search(query)
                Page.RESULTS, Page.SETTINGS, Page.FAVORITES -> showHome()
                Page.HOME -> finish()
            }
        })
        if (query.isBlank()) showHome() else search(query)
    }

    private fun search(text: String) {
        query = text.trim()
        saveQuery()
        if (query.isBlank()) showHome() else { page = Page.RESULTS; showList(); loadCars() }
    }
    private fun saveQuery() {
        getPreferences(MODE_PRIVATE).edit().apply {
            if (query.isBlank()) remove("last_query") else putString("last_query", query)
            apply()
        }
    }
    private fun showHome() {
        query = ""; page = Page.HOME; saveQuery()
        findViewById<SearchView>(R.id.carSearch).setQuery("", false)
        showList(); loadCars()
    }
    private fun showList() {
        findViewById<View>(R.id.catalogHeader).setBackgroundColor(getColor(if (page == Page.HOME) R.color.catalog_header else R.color.white))
        findViewById<TextView>(R.id.catalogTitle).apply {
            text = "Результаты поиска"
            visibility = if (page == Page.HOME) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.catalogBack).visibility = if (page == Page.RESULTS) View.VISIBLE else View.GONE
        findViewById<View>(R.id.homeLead).visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        findViewById<SearchView>(R.id.carSearch).visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.carList).visibility = View.VISIBLE
        findViewById<ScrollView>(R.id.catalogScroll).visibility = View.GONE
    }
    private fun loadCars() {
        findViewById<Button>(R.id.catalogRetry).setOnClickListener { loadCars() }
        val current = ++generation
        val requestedQuery = if (page == Page.RESULTS) query else ""
        showLoading(true)
        adapter.submit(emptyList())
        executor.execute {
            val result = runCatching { repository.cars(requestedQuery) }
            runOnUiThread {
                if (current != generation || isFinishing || isDestroyed) return@runOnUiThread
                showLoading(false)
                result.onSuccess { cars ->
                    adapter.submit(cars)
                    if (cars.isEmpty()) showMessage("Автомобили не найдены", false)
                }.onFailure { showMessage(if (requestedQuery.isBlank()) "Не удалось загрузить данные. Попробуйте снова." else "Не удалось выполнить поиск. Попробуйте снова.", true) }
            }
        }
    }
    private fun showSettings() {
        page = Page.SETTINGS; ++generation; showPanel("Настройки")
        val header = panels.settings(SecureSessionStore(this).load()?.email.orEmpty(),
            ::showProfile, ::showBookings, ::showAddCar) { title ->
            page = Page.SECTION; ++generation; showPanel(title)
            panels.text(title); panels.button("Назад", ::showSettings)
        }
        work({ repository.profile() }, { profile ->
            header.name.text = profile.name; header.email.text = profile.email
            loadAvatar(header.avatar, profile.avatarPath)
        }, { showError(it) })
    }

    private fun showProfile() {
        page = Page.PROFILE; ++generation; showPanel("Профиль")
        work({ repository.profile() }, { profile ->
            val avatar = panels.profile(profile, { avatarPicker.launch("image/*") }) {
                AuthRepository(this).clearSession()
                startActivity(Intent(this, AuthActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
            loadAvatar(avatar, profile.avatarPath)
        }, { showError(it) })
    }
    private fun loadAvatar(image: ImageView, path: String?) {
        path?.let {
            work({ repository.avatar(path) }, { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(image::setImageBitmap)
            }, { showError(it) })
        }
    }
    private fun openRental(car: Car) {
        startActivity(Intent(this, SectionActivity::class.java)
            .putExtra(SectionActivity.EXTRA_SECTION, SectionActivity.RENTAL)
            .putExtra(SectionActivity.EXTRA_NAME, "${car.brand} ${car.model}")
            .putExtra(SectionActivity.EXTRA_PRICE, "${car.price} ₽ за сутки")
            .putExtra(SectionActivity.EXTRA_PHOTO, car.photoResource()))
    }
    private fun showInfo(car: Car) {
        page = Page.DETAILS; ++generation; showPanel("Детали")
        findViewById<Button>(R.id.catalogRetry).setOnClickListener { showInfo(car) }
        work({ repository.car(car.id) }, { current ->
            panels.car(current) { openRental(current) }
        }, { showMessage("Не удалось загрузить данные. Попробуйте снова.", true) })
    }

    private fun showBookings() = openSection(SectionActivity.BOOKINGS)
    private fun showAddCar() = openSection(SectionActivity.ADD_CAR)
    private fun openSection(section: String) {
        startActivity(Intent(this, SectionActivity::class.java)
            .putExtra(SectionActivity.EXTRA_SECTION, section))
    }
    private fun showPanel(title: String) {
        findViewById<View>(R.id.catalogHeader).setBackgroundColor(getColor(R.color.white))
        findViewById<TextView>(R.id.catalogTitle).apply { text = title; visibility = View.VISIBLE }
        findViewById<View>(R.id.catalogBack).visibility = if (page == Page.SETTINGS) View.GONE else View.VISIBLE
        findViewById<SearchView>(R.id.carSearch).visibility = View.GONE
        findViewById<View>(R.id.homeLead).visibility = View.GONE
        findViewById<RecyclerView>(R.id.carList).visibility = View.GONE
        showLoading(false)
        showMessage("", false)
        val content = findViewById<LinearLayout>(R.id.catalogContent)
        content.removeAllViews(); findViewById<ScrollView>(R.id.catalogScroll).visibility = View.VISIBLE
    }
    private fun showLoading(loading: Boolean) {
        findViewById<View>(R.id.catalogProgress).visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) showMessage("", false)
    }
    private fun showMessage(message: String, retry: Boolean) {
        findViewById<TextView>(R.id.catalogMessage).apply { text = message; visibility = if (message.isBlank()) View.GONE else View.VISIBLE }
        findViewById<Button>(R.id.catalogRetry).visibility = if (retry) View.VISIBLE else View.GONE
    }
    private fun showError(error: Throwable) = showMessage(error.message ?: "Произошла ошибка", false)
    private fun <T> work(operation: () -> T, success: (T) -> Unit, failure: (Throwable) -> Unit) {
        val current = generation
        showLoading(true)
        executor.execute { val result = runCatching(operation); runOnUiThread {
            if (!isFinishing && !isDestroyed && current == generation) { showLoading(false); result.onSuccess(success).onFailure(failure) }
        } }
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
