package com.example.tenant_landlorddisputedocumenter.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.example.tenant_landlorddisputedocumenter.databinding.ActivityLoginBinding
import com.example.tenant_landlorddisputedocumenter.ui.BaseActivity
import com.example.tenant_landlorddisputedocumenter.ui.fadeInSlideUp
import com.example.tenant_landlorddisputedocumenter.ui.shake
import com.example.tenant_landlorddisputedocumenter.ui.collectOnStart
import com.example.tenant_landlorddisputedocumenter.ui.auth.SplashActivity

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels { AuthViewModelFactory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.signInButton.fadeInSlideUp(120)
        var lastError: String? = null

        binding.signInButton.setOnClickListener {
            viewModel.signIn(
                binding.emailField.text?.toString().orEmpty(),
                binding.passwordField.text?.toString().orEmpty(),
            )
        }
        binding.forgotPasswordButton.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        binding.createAccountButton.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        collectOnStart(viewModel.state) { state ->
            binding.signInButton.isEnabled = !state.isLoading
            binding.signInButton.text = if (state.isLoading) getString(com.example.tenant_landlorddisputedocumenter.R.string.loading)
            else getString(com.example.tenant_landlorddisputedocumenter.R.string.action_sign_in)
            binding.errorText.visibility = if (state.errorMessage != null) android.view.View.VISIBLE else android.view.View.GONE
            binding.errorText.text = state.errorMessage
            if (state.errorMessage != null && state.errorMessage != lastError) {
                binding.emailField.shake()
                binding.errorText.shake()
            }
            lastError = state.errorMessage
            if (state.signedInUser != null) {
                startActivity(Intent(this, SplashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }
    }
}
