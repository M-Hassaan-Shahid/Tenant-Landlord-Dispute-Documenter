package com.example.tenant_landlorddisputedocumenter.util

object InputValidation {
    const val MAX_TEXT_LENGTH = 500
    const val MAX_ADDRESS_LENGTH = 200
    const val MIN_NAME_LENGTH = 2
    const val MIN_RENT = 1.0
    const val MAX_PROFILE_PHOTO_BYTES = 5 * 1024 * 1024L
    val ALLOWED_PROFILE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val PHONE_REGEX = Regex("^[0-9+\\-\\s()]{7,20}$")
    private val CNIC_REGEX = Regex("^[0-9]{5}-[0-9]{7}-[0-9]$")

    fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required."
        !EMAIL_REGEX.matches(email.trim()) -> "Enter a valid email address."
        else -> null
    }

    fun validatePassword(password: String, confirmPassword: String): String? = when {
        password.length < 6 -> "Password must be at least 6 characters."
        password != confirmPassword -> "Passwords don't match."
        else -> null
    }

    fun validateDisplayName(name: String): String? = when {
        name.isBlank() -> "Please enter your name."
        name.trim().length < MIN_NAME_LENGTH -> "Name must be at least $MIN_NAME_LENGTH characters."
        else -> null
    }

    fun validatePhone(phone: String): String? = when {
        phone.isBlank() -> "Phone number is required."
        !PHONE_REGEX.matches(phone.trim()) -> "Enter a valid phone number."
        else -> null
    }

    fun validateCnic(cnic: String): String? = when {
        cnic.isBlank() -> "CNIC is required."
        !CNIC_REGEX.matches(cnic.trim()) -> "CNIC must be in format 12345-1234567-1."
        else -> null
    }

    fun validateAddress(address: String): String? = when {
        address.isBlank() -> "Address is required."
        address.trim().length > MAX_ADDRESS_LENGTH -> "Address is too long."
        else -> null
    }

    fun validateFinancialAmount(raw: String, label: String): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        if (value < MIN_RENT) return null
        return value
    }

    fun validateTextLength(text: String, label: String, max: Int = MAX_TEXT_LENGTH): String? =
        if (text.length > max) "$label must be at most $max characters." else null

    fun trimToMax(text: String, max: Int = MAX_TEXT_LENGTH): String =
        text.trim().take(max)

}
