package com.example.tenant_landlorddisputedocumenter.ui.auth

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.example.tenant_landlorddisputedocumenter.MainActivity
import com.example.tenant_landlorddisputedocumenter.databinding.ActivitySplashBinding
import com.example.tenant_landlorddisputedocumenter.ui.BaseActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Entry point. Shows branded splash (matches UI prototype), then routes to
 * [MainActivity] or [OnboardingActivity]. Cloud sync runs on the dashboard.
 */
class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playEntranceAnimation()

        lifecycleScope.launch {
            val minDisplayMs = 900L
            val startMs = System.currentTimeMillis()

            val uid = container.firebaseAuth.currentUser?.uid

            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < minDisplayMs) delay(minDisplayMs - elapsed)

            if (uid != null) {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, OnboardingActivity::class.java))
            }
            finish()
        }
    }

    private fun playEntranceAnimation() {
        val density = resources.displayMetrics.density
        val offsetY = 20f * density

        fun animateIn(view: View, delayMs: Long) {
            view.alpha = 0f
            view.translationY = offsetY
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(1000L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }

        animateIn(binding.textLogo, 0L)
        animateIn(binding.textTagline, 200L)

        binding.progressLoader.alpha = 0f
        ObjectAnimator.ofFloat(binding.progressLoader, View.ALPHA, 0f, 1f).apply {
            startDelay = 400L
            duration = 600L
            interpolator = DecelerateInterpolator()
            doOnEnd { binding.progressLoader.alpha = 1f }
            start()
        }
    }
}
