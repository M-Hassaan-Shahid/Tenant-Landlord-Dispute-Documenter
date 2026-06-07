package com.example.tenant_landlorddisputedocumenter.data.remote

import com.example.tenant_landlorddisputedocumenter.domain.model.AppNotification
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/** Sends notifications via Cloud Function (Firestore rules deny direct client creates). */
class NotificationCloudFunctions(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) {
    suspend fun send(notification: AppNotification): String {
        val result = functions.getHttpsCallable("sendNotification").call(
            mapOf(
                "recipientUid" to notification.recipientUid,
                "type" to notification.type.name,
                "title" to notification.title,
                "body" to notification.body,
                "propertyId" to notification.propertyId,
            ),
        ).await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: error("Invalid notification response.")
        return data["id"] as? String ?: error("Notification id missing from server.")
    }
}
