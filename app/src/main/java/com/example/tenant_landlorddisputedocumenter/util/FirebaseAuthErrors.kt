package com.example.tenant_landlorddisputedocumenter.util

/**
 * Maps Firebase Auth / reCAPTCHA failures to actionable messages for the UI.
 */
object FirebaseAuthErrors {

    fun userMessage(throwable: Throwable?): String {
        if (throwable == null) return "Authentication failed."
        val combined = buildString {
            append(throwable.message.orEmpty())
            throwable.cause?.message?.let { append(' ').append(it) }
        }
        return when {
            combined.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                "Firebase sign-up is not configured for this build. Add your debug SHA-1 in " +
                    "Firebase Console → Project settings → Your Android app, enable Email/Password " +
                    "under Authentication, then download a new google-services.json and rebuild. " +
                    "Details: docs/FIREBASE_AUTH_SETUP.md"

            combined.contains("EMAIL_ALREADY_IN_USE", ignoreCase = true) ->
                "An account with this email already exists. Try signing in."

            combined.contains("INVALID_EMAIL", ignoreCase = true) ->
                "That email address is not valid."

            combined.contains("WEAK_PASSWORD", ignoreCase = true) ->
                "Password is too weak. Use at least 6 characters."

            combined.contains("NETWORK", ignoreCase = true) ||
                combined.contains("network error", ignoreCase = true) ->
                "Network error. Check your connection and try again."

            else -> throwable.localizedMessage ?: "Authentication failed."
        }
    }
}
