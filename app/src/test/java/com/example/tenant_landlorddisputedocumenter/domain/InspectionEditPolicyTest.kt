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
        moveInSubmittedAtMillis: Long? = null,
        moveOutSubmittedAtMillis: Long? = null,
    ) = PropertyEntity(
        id = "property-1",
        landlordId = "landlord",
        tenantId = "tenant",
        address = "123 St",
        rent = 1000.0,
        deposit = 500.0,
        leaseStartMillis = 1_700_000_000_000,
        leaseEndMillis = 1_700_086_400_000,
        inviteCode = "ABC123",
        status = status,
        moveInInspectionSubmittedAtMillis = moveInSubmittedAtMillis,
        moveOutInspectionSubmittedAtMillis = moveOutSubmittedAtMillis,
        createdAtMillis = 1_700_000_000_000,
        updatedAtMillis = 1_700_000_000_000,
    )

    @Test
    fun canEditStructure_allowsActiveMoveInBeforeSubmission() {
        assertTrue(InspectionEditPolicy.canEditStructure(property(PropertyStatus.ACTIVE)))
    }

    @Test
    fun canEditStructure_blocksActiveMoveInAfterSubmission() {
        assertFalse(
            InspectionEditPolicy.canEditStructure(
                property(
                    status = PropertyStatus.ACTIVE,
                    moveInSubmittedAtMillis = 1_700_000_123_456,
                ),
            ),
        )
    }

    @Test
    fun canEditStructure_blocksMoveOutAndClosed() {
        assertFalse(InspectionEditPolicy.canEditStructure(property(PropertyStatus.MOVE_OUT)))
        assertFalse(InspectionEditPolicy.canEditStructure(property(PropertyStatus.CLOSED)))
    }

    @Test
    fun canEditRecords_blocksAfterSubmission() {
        val activeOpen = property(PropertyStatus.ACTIVE)
        val activeSubmitted = property(PropertyStatus.ACTIVE, moveInSubmittedAtMillis = 1L)
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
        val submitted = property(PropertyStatus.ACTIVE, moveInSubmittedAtMillis = 1L)
        assertTrue(
            InspectionEditPolicy.validateCapture(submitted, InspectionPhase.MOVE_IN, "landlord") != null,
        )
    }

    @Test
    fun disputeWindowOpen_afterSubmit_beforeClosed() {
        val submitted = property(PropertyStatus.ACTIVE, moveInSubmittedAtMillis = 1L)
        val closed = submitted.copy(status = PropertyStatus.CLOSED)
        assertTrue(InspectionEditPolicy.disputeWindowOpen(submitted))
        assertFalse(InspectionEditPolicy.disputeWindowOpen(property(PropertyStatus.ACTIVE)))
        assertFalse(InspectionEditPolicy.disputeWindowOpen(closed))
    }

    @Test
    fun canRaiseDispute_tenantOnlyDuringWindow() {
        val submitted = property(PropertyStatus.ACTIVE, moveInSubmittedAtMillis = 1L)
        assertTrue(InspectionEditPolicy.canRaiseDispute(submitted, "tenant"))
        assertFalse(InspectionEditPolicy.canRaiseDispute(submitted, "landlord"))
        assertFalse(InspectionEditPolicy.canRaiseDispute(property(PropertyStatus.ACTIVE), "tenant"))
    }

    @Test
    fun validateDisputeEvidenceCapture_allowsTenantAfterSubmit() {
        val submitted = property(PropertyStatus.ACTIVE, moveInSubmittedAtMillis = 1L)
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
