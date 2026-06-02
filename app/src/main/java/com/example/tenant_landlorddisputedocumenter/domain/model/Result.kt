package com.example.tenant_landlorddisputedocumenter.domain.model

/**
 * A small Result-style wrapper used across repositories. We deliberately avoid `kotlin.Result`
 * because it's not designed to cross suspending boundaries cleanly.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: Throwable, val userMessage: String? = null) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) block(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(block: (Throwable) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(error)
    return this
}
