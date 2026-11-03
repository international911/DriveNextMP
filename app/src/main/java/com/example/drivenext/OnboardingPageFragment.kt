package com.example.drivenext

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment

class OnboardingPageFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.page_onboarding, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val page = requireArguments().getInt(ARG_PAGE).coerceIn(0, 2)
        val image = view.findViewById<ImageView>(R.id.pageImage)
        val title = view.findViewById<TextView>(R.id.pageTitle)
        val description = view.findViewById<TextView>(R.id.pageDescription)
        image.setImageResource(IMAGES[page])
        image.contentDescription = getString(IMAGE_DESCRIPTIONS[page])
        title.setText(TITLES[page])
        description.setText(DESCRIPTIONS[page])
    }

    companion object {
        private const val ARG_PAGE = "page"
        private val IMAGES = intArrayOf(R.drawable.onboarding_rent, R.drawable.onboarding_safe, R.drawable.onboarding_offers)
        private val IMAGE_DESCRIPTIONS = intArrayOf(R.string.rent_art_description, R.string.safe_art_description, R.string.offers_art_description)
        private val TITLES = intArrayOf(R.string.rent_title, R.string.safe_title, R.string.offers_title)
        private val DESCRIPTIONS = intArrayOf(R.string.rent_description, R.string.safe_description, R.string.offers_description)

        fun create(page: Int) = OnboardingPageFragment().apply {
            arguments = Bundle().apply { putInt(ARG_PAGE, page) }
        }
    }
}
