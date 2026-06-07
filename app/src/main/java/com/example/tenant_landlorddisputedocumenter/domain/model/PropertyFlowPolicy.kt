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
