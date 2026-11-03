package com.example.drivenext

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2

class MainActivity : FragmentActivity() {
    private lateinit var model: StartupViewModel
    private lateinit var connectivityManager: ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private var displayedScreen: Screen? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val splashTask = Runnable {
        model.finishSplash(NetworkChecker.hasInternet(this))
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this, StartupViewModel.Factory(OnboardingRepository(applicationContext)))[StartupViewModel::class.java]
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        render()
        if (model.screen == Screen.SPLASH) handler.postDelayed(splashTask, model.remainingSplashMillis())
    }

    override fun onStart() {
        super.onStart()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = refreshNetwork()
            override fun onAvailable(network: Network) = refreshNetwork()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refreshNetwork()
        }
        networkCallback = callback
        connectivityManager.registerDefaultNetworkCallback(callback)
        refreshNetwork()
    }

    override fun onStop() {
        networkCallback?.let(connectivityManager::unregisterNetworkCallback)
        networkCallback = null
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(splashTask)
        super.onDestroy()
    }

    private fun refreshNetwork() {
        runOnUiThread {
            model.onNetworkChanged(NetworkChecker.hasInternet(this))
            render()
        }
    }

    private fun render() {
        if (displayedScreen == model.screen) return
        displayedScreen = model.screen
        when (model.screen) {
            Screen.SPLASH -> setContentView(R.layout.screen_splash)
            Screen.OFFLINE -> {
                setContentView(R.layout.screen_offline)
                findViewById<Button>(R.id.retryButton).setOnClickListener { refreshNetwork() }
            }
            Screen.ONBOARDING -> showOnboarding()
            Screen.ENTRY -> setContentView(R.layout.screen_entry)
        }
    }

    private fun showOnboarding() {
        setContentView(R.layout.screen_onboarding)
        val pager = findViewById<ViewPager2>(R.id.onboardingPager)
        val next = findViewById<Button>(R.id.nextButton)
        val skip = findViewById<TextView>(R.id.skipButton)
        val indicators = findViewById<LinearLayout>(R.id.pageIndicators)
        pager.adapter = OnboardingAdapter(this)
        pager.setCurrentItem(model.onboardingPage, false)
        fun updatePage(position: Int) {
            model.onboardingPage = position
            next.setText(if (position == 2) R.string.lets_go else R.string.next)
            indicators.removeAllViews()
            repeat(3) { index ->
                val indicator = View(this)
                val width = if (index == position) 38 else 15
                val params = LinearLayout.LayoutParams(dp(width), dp(6))
                params.marginEnd = dp(8)
                indicator.layoutParams = params
                indicator.setBackgroundResource(if (index == position) R.drawable.indicator_active else R.drawable.indicator_inactive)
                indicators.addView(indicator)
            }
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updatePage(position)
        })
        updatePage(model.onboardingPage)
        next.setOnClickListener {
            if (pager.currentItem == 2) completeOnboarding()
            else pager.setCurrentItem(pager.currentItem + 1, true)
        }
        skip.setOnClickListener { completeOnboarding() }
    }

    private fun completeOnboarding() {
        model.completeOnboarding()
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
