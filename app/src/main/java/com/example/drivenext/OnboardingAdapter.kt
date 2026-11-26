package com.example.drivenext

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

// Даёт ViewPager2 три фрагмента, по одному на каждый экран приветствия.
class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    // Количество страниц, которое увидит ViewPager2.
    override fun getItemCount(): Int = 3
    // При запросе страницы создаём фрагмент с её номером.
    override fun createFragment(position: Int): Fragment = OnboardingPageFragment.create(position)
}
