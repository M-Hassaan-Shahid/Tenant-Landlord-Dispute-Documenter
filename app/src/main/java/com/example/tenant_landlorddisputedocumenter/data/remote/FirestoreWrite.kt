package com.example.tenant_landlorddisputedocumenter.data.remote

/** Thrown when a Firestore write required for cloud sync fails. */
class FirestoreWriteException(
    val operation: String,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

/** Runs a Firestore write and surfaces failures instead of swallowing them. */
suspend inline fun firestoreWrite(operation: String, crossinline block: suspend () -> Unit) {
    try {
        block()
    } catch (e: FirestoreWriteException) {
        throw e
    } catch (e: Exception) {
        throw FirestoreWriteException(
            operation = operation,
            message = "Failed to sync $operation to cloud: ${e.localizedMessage ?: "unknown error"}",
            cause = e,
        )
    }
}
