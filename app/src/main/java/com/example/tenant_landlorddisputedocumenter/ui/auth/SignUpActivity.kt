package com.example.tenant_landlorddisputedocumenter.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ActivitySignUpBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import com.example.tenant_landlorddisputedocumenter.ui.BaseActivity
import com.example.tenant_landlorddisputedocumenter.ui.collectOnStart
import com.example.tenant_landlorddisputedocumenter.ui.fadeInSlideUp
import com.example.tenant_landlorddisputedocumenter.ui.shake

class SignUpActivity : BaseActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val viewModel: AuthViewModel by viewModels { AuthViewModelFactory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.roleToggleGroup.check(R.id.buttonLandlord)
        binding.createAccountButton.fadeInSlideUp(120)
        var lastError: String? = null

        binding.createAccountButton.setOnClickListener {
            val role = if (binding.roleToggleGroup.checkedButtonId == R.id.buttonLandlord) {
                UserRole.LANDLORD
            } else {
                UserRole.TENANT
            }
            viewModel.signUp(
                email = binding.emailField.text?.toString().orEmpty(),
                password = binding.passwordField.text?.toString().orEmpty(),
                confirmPassword = binding.confirmPasswordField.text?.toString().orEmpty(),
                displayName = binding.nameField.text?.toString().orEmpty(),
                phone = binding.phoneField.text?.toString().orEmpty(),
                cnic = binding.cnicField.text?.toString().orEmpty(),
                role = role,
            )
        }

        collectOnStart(viewModel.state) { state ->
            binding.createAccountButton.isEnabled = !state.isLoading
            binding.createAccountButton.text = if (state.isLoading) getString(R.string.loading)
            else getString(R.string.action_create_account)
            binding.errorText.visibility = if (state.errorMessage != null) View.VISIBLE else View.GONE
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
