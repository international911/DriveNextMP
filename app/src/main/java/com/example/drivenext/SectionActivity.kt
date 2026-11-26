package com.example.drivenext

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

// Показывает раздел и сведения об автомобиле, полученные при переходе на экран.
class SectionActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_section)
        val section = intent.getStringExtra(EXTRA_SECTION)
        val title = when (section) {
            RENTAL -> "Оформление аренды"
            BOOKINGS -> "Мои бронирования"
            ADD_CAR -> "Добавление автомобиля"
            else -> { finish(); return }
        }
        findViewById<TextView>(R.id.destinationTitle).text = title
        findViewById<View>(R.id.destinationBack).setOnClickListener { finish() }
        if (section == RENTAL) {
            findViewById<View>(R.id.destinationCar).visibility = View.VISIBLE
            findViewById<ImageView>(R.id.destinationPhoto).setImageResource(
                intent.getIntExtra(EXTRA_PHOTO, R.drawable.ic_car),
            )
            findViewById<TextView>(R.id.destinationName).text = intent.getStringExtra(EXTRA_NAME).orEmpty()
            findViewById<TextView>(R.id.destinationPrice).text = intent.getStringExtra(EXTRA_PRICE).orEmpty()
        }
    }

    companion object {
        const val EXTRA_SECTION = "section"
        const val EXTRA_NAME = "car_name"
        const val EXTRA_PRICE = "car_price"
        const val EXTRA_PHOTO = "car_photo"
        const val RENTAL = "rental"
        const val BOOKINGS = "bookings"
        const val ADD_CAR = "add_car"
    }
}
