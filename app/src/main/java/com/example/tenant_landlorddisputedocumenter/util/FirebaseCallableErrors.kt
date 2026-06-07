package com.example.tenant_landlorddisputedocumenter.util

import com.example.tenant_landlorddisputedocumenter.data.remote.FirestoreWriteException

/** Maps backend sync failures to UI-friendly messages. */
object FirebaseCallableErrors {

    fun userMessage(throwable: Throwable?): String {
        if (throwable == null) return "Something went wrong. Please try again."
        if (throwable is FirestoreWriteException) {
            return throwable.message ?: "Could not sync to cloud. Check your connection and try again."
        }
        val combined = buildString {
            append(throwable.message.orEmpty())
            throwable.cause?.message?.let { append(' ').append(it) }
            throwable.localizedMessage?.let { if (it !in this) append(' ').append(it) }
        }
        return when {
            combined.contains("NOT_FOUND", ignoreCase = true) ||
                combined.contains("not-found", ignoreCase = true) ||
                combined.contains("error_notfound", ignoreCase = true) ->
                "Required cloud data was not found yet. Refresh and try again on a stable connection."

            combined.contains("failed-precondition", ignoreCase = true) &&
                combined.contains("Cloudinary", ignoreCase = true) ->
                "Photo upload is not configured. Check the Cloudinary upload preset and try again."

            combined.contains("PERMISSION_DENIED", ignoreCase = true) ||
                combined.contains("permission-denied", ignoreCase = true) ->
                "You do not have permission to complete this action."

            combined.contains("UNAUTHENTICATED", ignoreCase = true) ->
                "Session expired. Please sign in again."

            combined.contains("NETWORK", ignoreCase = true) ||
                combined.contains("network", ignoreCase = true) ||
                combined.contains("Unable to resolve host", ignoreCase = true) ->
                "Network error. Check your connection and try again."

            else -> throwable.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Something went wrong. Please try again."
        }
    }
}
