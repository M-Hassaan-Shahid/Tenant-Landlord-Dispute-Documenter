package com.example.tenant_landlorddisputedocumenter.domain.model

/** Categories of in-app notifications used to drive UI grouping and deep-linking. */
enum class NotificationType {
    TENANT_JOINED,
    TENANT_APPROVED,
    TENANT_REJECTED,
    INSPECTION_SUBMITTED,
    SIGNATURE_REQUESTED,
    DISPUTE_RAISED,
    DISPUTE_RESOLVED,
    LEASE_ENDING,
    GENERIC;

    companion object {
        fun from(raw: String?): NotificationType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GENERIC
    }
}

/** A single notification entry persisted locally for the in-app notification center. */
data class AppNotification(
    val id: String,
    val recipientUid: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val propertyId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val read: Boolean = false,
)
