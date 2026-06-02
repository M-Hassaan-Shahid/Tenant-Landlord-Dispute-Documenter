package com.example.tenant_landlorddisputedocumenter.data

/** Aggregated outcome of a multi-step cloud sync. */
data class SyncResult(
    val errors: List<String> = emptyList(),
) {
    val succeeded: Boolean get() = errors.isEmpty()

    fun merge(other: SyncResult): SyncResult =
        SyncResult(errors = errors + other.errors)

    companion object {
        fun ok(): SyncResult = SyncResult()

        fun from(step: String, error: Throwable?): SyncResult =
            if (error == null) ok() else SyncResult(listOf("$step: ${error.localizedMessage ?: "unknown error"}"))
    }
}
