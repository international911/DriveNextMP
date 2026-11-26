package com.example.drivenext

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.OffsetDateTime

// Создаёт содержимое настроек, профиля и карточки; запросы выполняет экран каталога.
class CatalogPanels(private val activity: CatalogActivity) {
    data class SettingsHeader(val name: TextView, val email: TextView, val avatar: ImageView)
    private val content: LinearLayout get() = activity.findViewById(R.id.catalogContent)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun label(value: String, size: Float, color: Int) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(activity.getColor(color))
    }
    private fun round(image: ImageView) {
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(activity.getColor(R.color.soft_background))
        }
        image.clipToOutline = true
    }

    fun settings(email: String, onProfile: () -> Unit, onBookings: () -> Unit,
                 onAddCar: () -> Unit, onOther: (String) -> Unit): SettingsHeader {
        val account = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, dp(24)); setOnClickListener { onProfile() }
        }
        val avatar = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(70))
            setImageResource(R.drawable.ic_avatar); contentDescription = "Аватар"; round(this)
        }
        val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, 0, 0) }
        val name = label("Пользователь", 18f, R.color.purple)
        val emailView = label(email, 14f, R.color.field_hint)
        labels.addView(name); labels.addView(emailView)
        account.addView(avatar); account.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        account.addView(chevron()); content.addView(account)
        menuRow(R.drawable.ic_booking, "Мои бронирования", onBookings)
        menuRow(R.drawable.ic_theme, "Тема") { onOther("Тема") }
        menuRow(R.drawable.ic_notification, "Уведомления") { onOther("Уведомления") }
        menuRow(R.drawable.ic_connect_car, "Подключить свой автомобиль", onAddCar)
        divider()
        menuRow(R.drawable.ic_help, "Помощь") { onOther("Помощь") }
        menuRow(R.drawable.ic_invite, "Пригласи друга") { onOther("Пригласи друга") }
        return SettingsHeader(name, emailView, avatar)
    }

    fun profile(profile: UserProfile, onAvatar: () -> Unit, onExit: () -> Unit): ImageView {
        val avatar = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(112)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(24)
            }
            setImageResource(R.drawable.ic_avatar); contentDescription = "Изменить аватар"
            round(this); setOnClickListener { onAvatar() }
        }
        content.addView(avatar)
        content.addView(label(profile.name, 24f, R.color.text).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(6))
        })
        val joined = profile.createdAt?.let { runCatching {
            val date = OffsetDateTime.parse(it)
            val month = listOf("январе", "феврале", "марте", "апреле", "мае", "июне", "июле",
                "августе", "сентябре", "октябре", "ноябре", "декабре")[date.monthValue - 1]
            "Присоединился в $month ${date.year}"
        }.getOrNull() }
        content.addView(label(joined ?: "Нажмите на фото, чтобы сменить аватар", 14f, R.color.muted).apply {
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(26))
        })
        detailRow("Электронная почта", profile.email)
        detailRow("Пароль", "Изменить пароль")
        profile.gender?.let { detailRow("Пол", if (it == "male") "Мужской" else "Женский") }
        menuRow(null, "Выйти из профиля", onExit)
        return avatar
    }

    fun car(car: Car, onBook: () -> Unit) {
        content.addView(ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(220))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(car.photoResource())
            contentDescription = "Фото ${car.brand} ${car.model}"
        })
        content.addView(label("${car.brand} ${car.model}", 25f, R.color.text).apply {
            setPadding(0, dp(16), 0, dp(8))
        })
        detailRow("Стоимость", "${car.price} ₽ за сутки")
        detailRow("Характеристики", car.features)
        button("Забронировать", onBook)
    }

    fun text(value: String) { content.addView(TextView(activity).apply {
        text = value; textSize = 20f; setPadding(8, 12, 8, 12)
    }) }
    fun button(title: String, action: () -> Unit) {
        content.addView(Button(activity).apply { text = title; setOnClickListener { action() } })
    }
    private fun divider() {
        content.addView(View(activity).apply { setBackgroundColor(activity.getColor(R.color.divider)) },
            LinearLayout.LayoutParams(-1, dp(1)))
    }
    private fun chevron() = ImageView(activity).apply {
        layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        setImageResource(R.drawable.ic_chevron_right)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun menuRow(icon: Int?, title: String, action: () -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, dp(18)); setOnClickListener { action() }
        }
        val iconHolder = if (icon == null) View(activity) else ImageView(activity).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(iconHolder, LinearLayout.LayoutParams(dp(32), dp(24)))
        row.addView(label(title, 17f, R.color.purple), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(chevron()); content.addView(row)
    }
    private fun detailRow(title: String, value: String) {
        divider()
        content.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(18), 0, dp(18))
            addView(label(title, 16f, R.color.text)); addView(label(value, 14f, R.color.muted))
        })
    }
}
