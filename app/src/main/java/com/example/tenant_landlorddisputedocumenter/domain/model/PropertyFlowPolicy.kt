package com.example.tenant_landlorddisputedocumenter.domain.model

/** Guards navigation and screen entry for property-scoped flows. */
object PropertyFlowPolicy {

    data class Access(val allowed: Boolean, val denialMessage: String? = null)

    fun memberAccess(property: Property?, currentUid: String?): Access {
        if (currentUid.isNullOrBlank()) {
            return Access(false, "Not signed in.")
        }
        if (property == null) {
            return Access(false, "Property not found.")
        }
        if (property.landlordId != currentUid && property.tenantId != currentUid) {
            return Access(false, "You are not a member of this property.")
        }
        return Access(true)
    }

    fun compareAccess(property: Property?, currentUid: String?): Access {
        val base = memberAccess(property, currentUid)
        if (!base.allowed) return base
        if (property!!.status != PropertyStatus.MOVE_OUT && property.status != PropertyStatus.CLOSED) {
            return Access(false, "Compare is available during move-out or after the record is closed.")
        }
        return Access(true)
    }

    fun reportAccess(property: Property?, currentUid: String?): Access {
        val base = memberAccess(property, currentUid)
        if (!base.allowed) return base
        if (property!!.status != PropertyStatus.MOVE_OUT && property.status != PropertyStatus.CLOSED) {
            return Access(false, "Report is available during move-out or after the record is closed.")
        }
        return Access(true)
    }

    fun canShowRaiseDispute(property: Property, currentUid: String?): Boolean =
        InspectionEditPolicy.canRaiseDispute(
            property = property.toEntity(),
            callerUid = currentUid.orEmpty(),
        )

    fun roomSetupAccess(property: Property?, currentUid: String?): Access {
        val base = memberAccess(property, currentUid)
        if (!base.allowed) return base
        if (property!!.landlordId != currentUid) {
            return Access(false, "Only the landlord can set up rooms.")
        }
        if (property.status != PropertyStatus.ACTIVE) {
            return Access(false, "Room setup is only available while the property is active.")
        }
        if (property.moveInInspectionSubmittedAtMillis != null) {
            return Access(false, "Room structure is locked after move-in inspection is submitted.")
        }
        return Access(true)
    }

    fun inspectionAccess(
        property: Property?,
        currentUid: String?,
        phase: InspectionPhase,
    ): Access {
        val base = memberAccess(property, currentUid)
        if (!base.allowed) return base
        if (property!!.landlordId != currentUid) {
            return Access(false, "Only the landlord can document inspections.")
        }
        return when (phase) {
            InspectionPhase.MOVE_IN -> when {
                property.status == PropertyStatus.ACTIVE &&
                    property.moveInInspectionSubmittedAtMillis == null -> Access(true)
                else -> Access(false, "Move-in inspection is not open for this property.")
            }
            InspectionPhase.MOVE_OUT -> when {
                property.status == PropertyStatus.MOVE_OUT &&
                    property.moveOutInspectionSubmittedAtMillis == null -> Access(true)
                else -> Access(false, "Move-out inspection is not open for this property.")
            }
        }
    }

    fun reviewSignAccess(
        property: Property?,
        currentUid: String?,
        phase: InspectionPhase,
    ): Access {
        val base = memberAccess(property, currentUid)
        if (!base.allowed) return base
        if (property!!.status == PropertyStatus.CLOSED) {
            return Access(false, "This property record is closed.")
        }
        val submitted = when (phase) {
            InspectionPhase.MOVE_IN -> property.moveInInspectionSubmittedAtMillis != null
            InspectionPhase.MOVE_OUT -> property.moveOutInspectionSubmittedAtMillis != null
        }
        if (!submitted) {
            return Access(false, "Inspection must be submitted before review and signing.")
        }
        return Access(true)
    }

    private fun Property.toEntity() = com.example.tenant_landlorddisputedocumenter.data.local.entity.PropertyEntity(
        id = id,
        landlordId = landlordId,
        tenantId = tenantId,
        address = address,
        rent = rent,
        deposit = deposit,
        leaseStartMillis = leaseStartMillis,
        leaseEndMillis = leaseEndMillis,
        inviteCode = inviteCode,
        status = status,
        moveInInspectionSubmittedAtMillis = moveInInspectionSubmittedAtMillis,
        moveOutInspectionSubmittedAtMillis = moveOutInspectionSubmittedAtMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}
