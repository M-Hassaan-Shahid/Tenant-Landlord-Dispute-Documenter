package com.example.tenant_landlorddisputedocumenter.data.remote

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/** Fetches a server-signed Cloudinary upload payload (API secret stays in Cloud Functions). */
class CloudinarySignatureProvider(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) {
    data class SignedParams(
        val cloudName: String,
        val apiKey: String,
        val timestamp: Long,
        val signature: String,
        val folder: String,
    )

    suspend fun fetch(folder: String): SignedParams {
        val result = functions
            .getHttpsCallable("getCloudinaryUploadParams")
            .call(mapOf("folder" to folder))
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: error("Invalid signature response.")
        return SignedParams(
            cloudName = data["cloudName"] as? String ?: error("Missing cloudName."),
            apiKey = data["apiKey"] as? String ?: error("Missing apiKey."),
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: error("Missing timestamp."),
            signature = data["signature"] as? String ?: error("Missing signature."),
            folder = data["folder"] as? String ?: folder,
        )
    }
}
