package com.example.tenant_landlorddisputedocumenter.ui.auth

import android.content.Intent
import android.os.Bundle
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ActivityOnboardingBinding
import com.example.tenant_landlorddisputedocumenter.databinding.ItemOnboardingFeatureBinding
import com.example.tenant_landlorddisputedocumenter.ui.BaseActivity

/** Welcome screen with feature highlights, Sign-up and Login. */
class OnboardingActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindFeature(
            binding.featurePhotos,
            R.drawable.ic_photo_camera,
            R.string.feature_photos_title,
            R.string.feature_photos_body,
        )
        bindFeature(
            binding.featureSign,
            R.drawable.ic_edit,
            R.string.feature_sign_title,
            R.string.feature_sign_body,
        )
        bindFeature(
            binding.featureReport,
            R.drawable.ic_description,
            R.string.feature_report_title,
            R.string.feature_report_body,
        )

        binding.signUpButton.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
        binding.loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun bindFeature(
        feature: ItemOnboardingFeatureBinding,
        iconRes: Int,
        titleRes: Int,
        bodyRes: Int,
    ) {
        feature.iconFeature.setImageResource(iconRes)
        feature.textFeatureTitle.setText(titleRes)
        feature.textFeatureBody.setText(bodyRes)
    }
}
