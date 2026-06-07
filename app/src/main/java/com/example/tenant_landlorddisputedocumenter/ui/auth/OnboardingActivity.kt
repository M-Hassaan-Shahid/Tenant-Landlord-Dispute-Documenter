package com.example.tenant_landlorddisputedocumenter.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ActivityOnboardingBinding
import com.example.tenant_landlorddisputedocumenter.ui.BaseActivity
import com.example.tenant_landlorddisputedocumenter.ui.fadeInSlideUp
import com.example.tenant_landlorddisputedocumenter.ui.pulse

/** Welcome carousel with feature highlights, Sign-up and Login. */
class OnboardingActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val dotViews = mutableListOf<View>()

    private val pages = listOf(
        OnboardingPage(
            R.drawable.ic_photo_camera,
            R.string.feature_photos_title,
            R.string.feature_photos_body,
        ),
        OnboardingPage(
            R.drawable.ic_edit,
            R.string.feature_sign_title,
            R.string.feature_sign_body,
        ),
        OnboardingPage(
            R.drawable.ic_description,
            R.string.feature_report_title,
            R.string.feature_report_body,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarPadding()
        setupPager()
        setupDots()

        binding.signUpButton.fadeInSlideUp(80)
        binding.loginButton.fadeInSlideUp(140)

        binding.signUpButton.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
        binding.loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun applySystemBarPadding() {
        val baseTop = resources.getDimensionPixelSize(R.dimen.onboarding_section_spacing)
        val baseBottom = resources.getDimensionPixelSize(R.dimen.onboarding_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { view, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(
                top = statusBars.top + baseTop,
                bottom = navBars.bottom + baseBottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.onboardingRoot)
    }

    private fun setupPager() {
        binding.viewPagerOnboarding.adapter = OnboardingPagerAdapter(pages)
        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
            }
        })
    }

    private fun setupDots() {
        binding.layoutDots.removeAllViews()
        dotViews.clear()

        val dotSize = resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)
        val activeWidth = resources.getDimensionPixelSize(R.dimen.onboarding_dot_active_width)
        val gap = resources.getDimensionPixelSize(R.dimen.onboarding_dot_gap)
        val touchSize = resources.getDimensionPixelSize(R.dimen.onboarding_dot_touch)
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_onboarding_dot_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_onboarding_dot_inactive)

        pages.indices.forEach { index ->
            val touchTarget = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(touchSize, touchSize).apply {
                    if (index > 0) marginStart = gap
                }
                isClickable = true
                isFocusable = true
                contentDescription = getString(
                    R.string.onboarding_page_indicator,
                    index + 1,
                    pages.size,
                )
                setOnClickListener { binding.viewPagerOnboarding.setCurrentItem(index, true) }
            }

            val dot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    if (index == 0) activeWidth else dotSize,
                    dotSize,
                    Gravity.CENTER,
                )
                background = if (index == 0) activeBg else inactiveBg
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            touchTarget.addView(dot)
            dotViews.add(dot)
            binding.layoutDots.addView(touchTarget)
        }
    }

    private fun updateDots(selectedIndex: Int) {
        val dotSize = resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)
        val activeWidth = resources.getDimensionPixelSize(R.dimen.onboarding_dot_active_width)
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_onboarding_dot_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_onboarding_dot_inactive)

        dotViews.forEachIndexed { index, dot ->
            val active = index == selectedIndex
            val lp = dot.layoutParams as FrameLayout.LayoutParams
            lp.width = if (active) activeWidth else dotSize
            dot.layoutParams = lp
            dot.background = if (active) activeBg else inactiveBg
            if (active) dot.pulse(1.15f)
        }
    }
}
