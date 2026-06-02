package com.example.tenant_landlorddisputedocumenter.domain.model

/** Status of a dispute raised against a checklist item. */
enum class DisputeStatus {
    OPEN,
    RESOLVED,
    UNRESOLVED;

    companion object {
        fun from(raw: String?): DisputeStatus = values().firstOrNull { it.name == raw?.uppercase() } ?: OPEN
    }
}

/** A formal disagreement raised by either party on a specific checklist item. */
data class Dispute(
    val id: String,
    val propertyId: String,
    val itemId: String,
    val raisedByUid: String,
    val raisedByRole: UserRole,
    val reason: String,
    val counterPhotoIds: List<String> = emptyList(),
    val counterNote: String = "",
    val resolutionNote: String = "",
    val status: DisputeStatus = DisputeStatus.OPEN,
    val raisedAtMillis: Long = System.currentTimeMillis(),
    val resolvedAtMillis: Long? = null,
)
