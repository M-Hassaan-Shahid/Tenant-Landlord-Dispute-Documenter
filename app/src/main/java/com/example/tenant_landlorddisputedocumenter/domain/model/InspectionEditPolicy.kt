package com.example.tenant_landlorddisputedocumenter.domain.model

import com.example.tenant_landlorddisputedocumenter.data.local.entity.PropertyEntity

/** Central rules for when inspection data may be edited or captured. */
object InspectionEditPolicy {

    fun canEditRecords(property: PropertyEntity): Boolean {
        return when (property.status) {
            PropertyStatus.ACTIVE -> property.moveInInspectionSubmittedAtMillis == null
            PropertyStatus.MOVE_OUT -> property.moveOutInspectionSubmittedAtMillis == null
            else -> false
        }
    }

    fun canEditRecordsForPhase(property: PropertyEntity, phase: InspectionPhase): Boolean {
        if (!canEditRecords(property)) return false
        return when (phase) {
            InspectionPhase.MOVE_IN -> property.status == PropertyStatus.ACTIVE
            InspectionPhase.MOVE_OUT -> property.status == PropertyStatus.MOVE_OUT
        }
    }

    fun validateCapture(
        property: PropertyEntity,
        phase: InspectionPhase,
        callerUid: String,
    ): String? {
        if (property.landlordId != callerUid) {
            return "Only the landlord can capture inspection photos."
        }
        if (!canEditRecordsForPhase(property, phase)) {
            return when (phase) {
                InspectionPhase.MOVE_IN -> "Move-in inspection is no longer open for photos."
                InspectionPhase.MOVE_OUT -> "Move-out inspection is no longer open for photos."
            }
        }
        return null
    }

    /** True after landlord submits inspection and before the property is closed. */
    fun disputeWindowOpen(property: PropertyEntity): Boolean {
        if (property.status == PropertyStatus.CLOSED || property.tenantId == null) return false
        return when (property.status) {
            PropertyStatus.ACTIVE -> property.moveInInspectionSubmittedAtMillis != null
            PropertyStatus.MOVE_OUT -> property.moveOutInspectionSubmittedAtMillis != null
            else -> false
        }
    }

    fun canRaiseDispute(property: PropertyEntity, callerUid: String): Boolean =
        property.tenantId == callerUid && disputeWindowOpen(property)

    fun validateDisputeEvidenceCapture(
        property: PropertyEntity,
        phase: InspectionPhase,
        callerUid: String,
    ): String? {
        if (property.tenantId != callerUid) {
            return "Only the tenant can attach dispute evidence."
        }
        if (!disputeWindowOpen(property)) {
            return "Disputes are only available during review before the record is signed."
        }
        val phaseMatches = when (phase) {
            InspectionPhase.MOVE_IN -> property.status == PropertyStatus.ACTIVE
            InspectionPhase.MOVE_OUT -> property.status == PropertyStatus.MOVE_OUT
        }
        if (!phaseMatches) {
            return "This inspection phase is not open for dispute evidence."
        }
        return null
    }
}
