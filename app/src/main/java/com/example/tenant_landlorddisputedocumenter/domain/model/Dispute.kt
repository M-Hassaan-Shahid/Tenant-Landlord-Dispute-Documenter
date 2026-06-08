package com.example.tenant_landlorddisputedocumenter.domain.model

/**
 * Status of a dispute raised against a checklist item.
 *
 * Lifecycle: a tenant raises a dispute ([OPEN]); the other party (landlord) proposes a
 * resolution, moving it to [AWAITING_TENANT_CONFIRMATION]; the tenant who raised it then
 * either confirms ([RESOLVED]) or rejects ([UNRESOLVED]). This two-party hand-off is the
 * "check" that stops a landlord from unilaterally declaring a dispute resolved.
 */
enum class DisputeStatus {
    OPEN,
    AWAITING_TENANT_CONFIRMATION,
    RESOLVED,
    UNRESOLVED;

    /** True while the item is still contested and must stay locked. */
    val isActive: Boolean get() = this == OPEN || this == AWAITING_TENANT_CONFIRMATION

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
    /** Resolution text proposed by the non-raiser (landlord). */
    val resolutionNote: String = "",
    /** Uid of the party who proposed the resolution; blank until a proposal is made. */
    val proposedByUid: String = "",
    /** Optional note the raiser (tenant) leaves when confirming or rejecting the proposal. */
    val tenantResponseNote: String = "",
    val status: DisputeStatus = DisputeStatus.OPEN,
    val raisedAtMillis: Long = System.currentTimeMillis(),
    val resolvedAtMillis: Long? = null,
)
