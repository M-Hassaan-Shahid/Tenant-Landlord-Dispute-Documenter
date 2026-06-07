package com.example.tenant_landlorddisputedocumenter.domain

import com.example.tenant_landlorddisputedocumenter.data.local.entity.PropertyEntity
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionEditPolicy
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionEditPolicyTest {

    private fun property(
        status: PropertyStatus,
        moveInSubmitted: Long? = null,
        moveOutSubmitted: Long? = null,
    ) = PropertyEntity(
        id = "p1",
        landlordId = "landlord",
        tenantId = "tenant",
        address = "123 St",
        rent = 1000.0,
        deposit = 500.0,
        leaseStartMillis = 0L,
        leaseEndMillis = 1L,
        inviteCode = "ABC123",
        status = status,
        moveInInspectionSubmittedAtMillis = moveInSubmitted,
        moveOutInspectionSubmittedAtMillis = moveOutSubmitted,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    @Test
    fun canEditRecords_blocksAfterSubmission() {
        val activeOpen = property(PropertyStatus.ACTIVE)
        val activeSubmitted = property(PropertyStatus.ACTIVE, moveInSubmitted = 1L)
        assertTrue(InspectionEditPolicy.canEditRecords(activeOpen))
        assertFalse(InspectionEditPolicy.canEditRecords(activeSubmitted))
    }

    @Test
    fun validateCapture_requiresLandlordAndOpenPhase() {
        val active = property(PropertyStatus.ACTIVE)
        assertNull(InspectionEditPolicy.validateCapture(active, InspectionPhase.MOVE_IN, "landlord"))
        assertTrue(
            InspectionEditPolicy.validateCapture(active, InspectionPhase.MOVE_IN, "tenant") != null,
        )
        val submitted = property(PropertyStatus.ACTIVE, moveInSubmitted = 1L)
        assertTrue(
            InspectionEditPolicy.validateCapture(submitted, InspectionPhase.MOVE_IN, "landlord") != null,
        )
    }

    @Test
    fun disputeWindowOpen_afterSubmit_beforeClosed() {
        val submitted = property(PropertyStatus.ACTIVE, moveInSubmitted = 1L)
        val closed = submitted.copy(status = PropertyStatus.CLOSED)
        assertTrue(InspectionEditPolicy.disputeWindowOpen(submitted))
        assertFalse(InspectionEditPolicy.disputeWindowOpen(property(PropertyStatus.ACTIVE)))
        assertFalse(InspectionEditPolicy.disputeWindowOpen(closed))
    }

    @Test
    fun canRaiseDispute_tenantOnlyDuringWindow() {
        val submitted = property(PropertyStatus.ACTIVE, moveInSubmitted = 1L)
        assertTrue(InspectionEditPolicy.canRaiseDispute(submitted, "tenant"))
        assertFalse(InspectionEditPolicy.canRaiseDispute(submitted, "landlord"))
        assertFalse(InspectionEditPolicy.canRaiseDispute(property(PropertyStatus.ACTIVE), "tenant"))
    }

    @Test
    fun validateDisputeEvidenceCapture_allowsTenantAfterSubmit() {
        val submitted = property(PropertyStatus.ACTIVE, moveInSubmitted = 1L)
        assertNull(
            InspectionEditPolicy.validateDisputeEvidenceCapture(
                submitted,
                InspectionPhase.MOVE_IN,
                "tenant",
            ),
        )
        assertTrue(
            InspectionEditPolicy.validateDisputeEvidenceCapture(
                submitted,
                InspectionPhase.MOVE_IN,
                "landlord",
            ) != null,
        )
    }
}
