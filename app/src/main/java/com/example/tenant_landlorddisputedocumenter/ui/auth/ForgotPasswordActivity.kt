package com.example.tenant_landlorddisputedocumenter.ui.auth

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import com.example.tenant_landlorddisputedocumenter.databinding.ActivityForgotPasswordBinding
import com.example.tenant_landlorddisputedocumenter.ui.BaseActivity
import com.example.tenant_landlorddisputedocumenter.ui.collectOnStart

class ForgotPasswordActivity : BaseActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val viewModel: AuthViewModel by viewModels { AuthViewModelFactory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.sendResetButton.setOnClickListener {
            viewModel.sendPasswordReset(binding.emailField.text?.toString().orEmpty())
        }

        collectOnStart(viewModel.state) { state ->
            binding.sendResetButton.isEnabled = !state.isLoading
            binding.errorText.visibility = if (state.errorMessage != null) View.VISIBLE else View.GONE
            binding.errorText.text = state.errorMessage
            binding.infoText.visibility = if (state.infoMessage != null) View.VISIBLE else View.GONE
            binding.infoText.text = state.infoMessage
        }
    }
}
