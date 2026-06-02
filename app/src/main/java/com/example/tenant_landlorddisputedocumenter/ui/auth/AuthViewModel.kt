package com.example.tenant_landlorddisputedocumenter.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.domain.model.User
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val signedInUser: User? = null,
    val resetSent: Boolean = false,
)

/**
 * Shared view-model for all auth screens (sign-up, sign-in, forgot-password). Each screen
 * uses only the subset of state it needs.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val currentUserId: StateFlow<String?> = authRepository.currentUserId

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(errorMessage = "Enter email and password.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.signIn(email, password)) {
                is Outcome.Success -> _state.update {
                    it.copy(isLoading = false, signedInUser = result.value)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.userMessage ?: "Sign-in failed.")
                }
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        confirmPassword: String,
        displayName: String,
        phone: String,
        cnic: String,
        role: UserRole,
    ) {
        val problem = validateSignUp(email, password, confirmPassword, displayName)
        if (problem != null) {
            _state.update { it.copy(errorMessage = problem) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.signUp(email, password, displayName, phone, cnic, role)) {
                is Outcome.Success -> _state.update {
                    it.copy(isLoading = false, signedInUser = result.value)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.userMessage ?: "Sign-up failed.")
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _state.update { it.copy(errorMessage = "Enter your email first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.sendPasswordReset(email)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Reset link sent to $email.",
                        resetSent = true,
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.userMessage ?: "Reset failed.")
                }
            }
        }
    }

    fun signOut() = authRepository.signOut()

    private fun validateSignUp(
        email: String,
        password: String,
        confirmPassword: String,
        displayName: String,
    ): String? = when {
        email.isBlank() -> "Email is required."
        !email.contains("@") -> "Enter a valid email."
        password.length < 6 -> "Password must be at least 6 characters."
        password != confirmPassword -> "Passwords don't match."
        displayName.isBlank() -> "Please enter your name."
        else -> null
    }
}

class AuthViewModelFactory(private val container: ServiceContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AuthViewModel(container.authRepository) as T
}
