package com.example.tenant_landlorddisputedocumenter.domain

import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyFlowPolicy
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyFlowPolicyTest {

    private fun property(status: PropertyStatus) = Property(
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
        moveInInspectionSubmittedAtMillis = if (status == PropertyStatus.ACTIVE) null else 1L,
        moveOutInspectionSubmittedAtMillis = if (status == PropertyStatus.MOVE_OUT) 1L else null,
    )

    @Test
    fun memberAccess_deniesNonMember() {
        val access = PropertyFlowPolicy.memberAccess(property(PropertyStatus.ACTIVE), "stranger")
        assertFalse(access.allowed)
    }

    @Test
    fun compareAccess_requiresMoveOutOrClosed() {
        assertFalse(PropertyFlowPolicy.compareAccess(property(PropertyStatus.ACTIVE), "tenant").allowed)
        assertTrue(PropertyFlowPolicy.compareAccess(property(PropertyStatus.MOVE_OUT), "tenant").allowed)
        assertTrue(PropertyFlowPolicy.compareAccess(property(PropertyStatus.CLOSED), "landlord").allowed)
    }

    @Test
    fun canShowRaiseDispute_onlyTenantDuringReview() {
        val activeSubmitted = property(PropertyStatus.ACTIVE).copy(moveInInspectionSubmittedAtMillis = 1L)
        assertTrue(PropertyFlowPolicy.canShowRaiseDispute(activeSubmitted, "tenant"))
        assertFalse(PropertyFlowPolicy.canShowRaiseDispute(activeSubmitted, "landlord"))
    }

    @Test
    fun roomSetupAccess_landlordOnlyBeforeSubmit() {
        val active = property(PropertyStatus.ACTIVE)
        assertTrue(PropertyFlowPolicy.roomSetupAccess(active, "landlord").allowed)
        assertFalse(PropertyFlowPolicy.roomSetupAccess(active, "tenant").allowed)
        val submitted = active.copy(moveInInspectionSubmittedAtMillis = 1L)
        assertFalse(PropertyFlowPolicy.roomSetupAccess(submitted, "landlord").allowed)
    }

    @Test
    fun inspectionAccess_landlordDuringOpenPhase() {
        val active = property(PropertyStatus.ACTIVE)
        assertTrue(
            PropertyFlowPolicy.inspectionAccess(active, "landlord", InspectionPhase.MOVE_IN).allowed,
        )
        assertFalse(
            PropertyFlowPolicy.inspectionAccess(active, "tenant", InspectionPhase.MOVE_IN).allowed,
        )
    }

    @Test
    fun reviewSignAccess_requiresSubmittedInspection() {
        val active = property(PropertyStatus.ACTIVE)
        assertFalse(
            PropertyFlowPolicy.reviewSignAccess(active, "tenant", InspectionPhase.MOVE_IN).allowed,
        )
        val submitted = active.copy(moveInInspectionSubmittedAtMillis = 1L)
        assertTrue(
            PropertyFlowPolicy.reviewSignAccess(submitted, "tenant", InspectionPhase.MOVE_IN).allowed,
        )
    }
}
