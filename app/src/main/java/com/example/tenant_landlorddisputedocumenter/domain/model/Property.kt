package com.example.tenant_landlorddisputedocumenter.domain.model

/** A rental property tracked in ProofNest. */
data class Property(
    val id: String,
    val landlordId: String,
    val tenantId: String? = null,
    val address: String,
    val rent: Double,
    val deposit: Double,
    val leaseStartMillis: Long,
    val leaseEndMillis: Long,
    val inviteCode: String,
    val status: PropertyStatus = PropertyStatus.PENDING,
    val moveInInspectionSubmittedAtMillis: Long? = null,
    val moveOutInspectionSubmittedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)
