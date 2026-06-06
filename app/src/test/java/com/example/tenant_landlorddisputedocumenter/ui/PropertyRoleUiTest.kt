package com.example.tenant_landlorddisputedocumenter.ui

import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.Signature
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyPrimaryAction
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyRoleUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyRoleUiTest {

    private val landlordId = "landlord-1"
    private val tenantId = "tenant-1"

    private fun property(
        status: PropertyStatus,
        moveInSubmitted: Boolean = false,
        moveOutSubmitted: Boolean = false,
    ) = Property(
        id = "prop-1",
        landlordId = landlordId,
        tenantId = tenantId,
        address = "123 Test St",
        rent = 2000.0,
        deposit = 4000.0,
        leaseStartMillis = 1L,
        leaseEndMillis = 2L,
        inviteCode = "ABC123",
        status = status,
        moveInInspectionSubmittedAtMillis = if (moveInSubmitted) 100L else null,
        moveOutInspectionSubmittedAtMillis = if (moveOutSubmitted) 200L else null,
    )

    private fun sig(uid: String, phase: InspectionPhase = InspectionPhase.MOVE_IN) = Signature(
        id = "sig-$uid-${phase.name}",
        propertyId = "prop-1",
        signerUid = uid,
        signerRole = if (uid == landlordId) UserRole.LANDLORD else UserRole.TENANT,
        phase = phase,
        signedAtMillis = 1L,
    )

    @Test
    fun landlord_active_before_submit_can_setup_and_document() {
        val state = PropertyRoleUi.resolve(property(PropertyStatus.ACTIVE), landlordId, emptyList(), emptyList())!!
        assertTrue(PropertyPrimaryAction.ROOM_SETUP in state.actions)
        assertTrue(PropertyPrimaryAction.DOCUMENT_MOVE_IN in state.actions)
        assertFalse(PropertyPrimaryAction.REVIEW_SIGN_MOVE_IN in state.actions)
        assertFalse(PropertyPrimaryAction.WAITING_LANDLORD_DOCUMENT in state.actions)
    }

    @Test
    fun tenant_active_before_submit_waits_for_landlord() {
        val state = PropertyRoleUi.resolve(property(PropertyStatus.ACTIVE), tenantId, emptyList(), emptyList())!!
        assertTrue(PropertyPrimaryAction.WAITING_LANDLORD_DOCUMENT in state.actions)
        assertFalse(PropertyPrimaryAction.DOCUMENT_MOVE_IN in state.actions)
        assertFalse(PropertyPrimaryAction.REVIEW_SIGN_MOVE_IN in state.actions)
        assertFalse(PropertyPrimaryAction.ROOM_SETUP in state.actions)
    }

    @Test
    fun tenant_sees_documenting_message_when_landlord_added_rooms() {
        val state = PropertyRoleUi.resolve(
            property(PropertyStatus.ACTIVE),
            tenantId,
            emptyList(),
            emptyList(),
            hasRooms = true,
        )!!
        assertTrue(PropertyPrimaryAction.WAITING_LANDLORD_DOCUMENT in state.actions)
        assertEquals(R.string.waiting_landlord_documenting_title, state.waitingCardTitleRes)
        assertEquals(R.string.waiting_landlord_documenting_body, state.waitingCardBodyRes)
    }

    @Test
    fun landlord_after_submit_can_sign_and_see_disputes() {
        val state = PropertyRoleUi.resolve(
            property(PropertyStatus.ACTIVE, moveInSubmitted = true),
            landlordId,
            emptyList(),
            emptyList(),
        )!!
        assertTrue(PropertyPrimaryAction.REVIEW_SIGN_MOVE_IN in state.actions)
        assertTrue(PropertyPrimaryAction.VIEW_DISPUTES in state.actions)
        assertFalse(PropertyPrimaryAction.DOCUMENT_MOVE_IN in state.actions)
    }

    @Test
    fun tenant_after_submit_can_review_sign_and_dispute() {
        val state = PropertyRoleUi.resolve(
            property(PropertyStatus.ACTIVE, moveInSubmitted = true),
            tenantId,
            emptyList(),
            emptyList(),
        )!!
        assertTrue(PropertyPrimaryAction.REVIEW_SIGN_MOVE_IN in state.actions)
        assertTrue(PropertyPrimaryAction.VIEW_DISPUTES in state.actions)
        assertFalse(PropertyPrimaryAction.DOCUMENT_MOVE_IN in state.actions)
    }

    @Test
    fun both_signed_move_in_becomes_occupied_actions() {
        val moveInSigs = listOf(sig(landlordId), sig(tenantId))
        val landlord = PropertyRoleUi.resolve(
            property(PropertyStatus.OCCUPIED),
            landlordId,
            moveInSigs,
            emptyList(),
        )!!
        val tenant = PropertyRoleUi.resolve(
            property(PropertyStatus.OCCUPIED),
            tenantId,
            moveInSigs,
            emptyList(),
        )!!
        assertTrue(PropertyPrimaryAction.START_MOVE_OUT in landlord.actions)
        assertFalse(PropertyPrimaryAction.START_MOVE_OUT in tenant.actions)
    }

    @Test
    fun move_out_landlord_documents_before_tenant_reviews() {
        val moveInSigs = listOf(sig(landlordId), sig(tenantId))
        val landlord = PropertyRoleUi.resolve(
            property(PropertyStatus.MOVE_OUT),
            landlordId,
            moveInSigs,
            emptyList(),
        )!!
        assertTrue(PropertyPrimaryAction.DOCUMENT_MOVE_OUT in landlord.actions)
        assertFalse(PropertyPrimaryAction.REVIEW_SIGN_MOVE_OUT in landlord.actions)
        assertFalse(PropertyPrimaryAction.VIEW_COMPARE in landlord.actions)

        val tenant = PropertyRoleUi.resolve(
            property(PropertyStatus.MOVE_OUT),
            tenantId,
            moveInSigs,
            emptyList(),
        )!!
        assertTrue(PropertyPrimaryAction.WAITING_LANDLORD_DOCUMENT in tenant.actions)
        assertFalse(PropertyPrimaryAction.DOCUMENT_MOVE_OUT in tenant.actions)
        assertEquals(R.string.waiting_landlord_move_out_title, tenant.waitingCardTitleRes)
    }

    @Test
    fun move_out_both_review_after_landlord_submits() {
        val moveInSigs = listOf(sig(landlordId), sig(tenantId))
        val landlord = PropertyRoleUi.resolve(
            property(PropertyStatus.MOVE_OUT, moveOutSubmitted = true),
            landlordId,
            moveInSigs,
            emptyList(),
        )!!
        assertFalse(PropertyPrimaryAction.DOCUMENT_MOVE_OUT in landlord.actions)
        assertTrue(PropertyPrimaryAction.REVIEW_SIGN_MOVE_OUT in landlord.actions)

        val tenant = PropertyRoleUi.resolve(
            property(PropertyStatus.MOVE_OUT, moveOutSubmitted = true),
            tenantId,
            moveInSigs,
            emptyList(),
        )!!
        assertTrue(PropertyPrimaryAction.REVIEW_SIGN_MOVE_OUT in tenant.actions)
        assertFalse(PropertyPrimaryAction.DOCUMENT_MOVE_OUT in tenant.actions)
    }

    @Test
    fun move_out_hides_document_after_party_signed() {
        val moveOutLandlordSigned = listOf(sig(landlordId, InspectionPhase.MOVE_OUT))
        val state = PropertyRoleUi.resolve(
            property(PropertyStatus.MOVE_OUT, moveInSubmitted = true),
            landlordId,
            listOf(sig(landlordId), sig(tenantId)),
            moveOutLandlordSigned,
        )!!
        assertFalse(PropertyPrimaryAction.DOCUMENT_MOVE_OUT in state.actions)
        assertFalse(PropertyPrimaryAction.REVIEW_SIGN_MOVE_OUT in state.actions)
        assertTrue(PropertyPrimaryAction.VIEW_COMPARE in state.actions)
    }

    @Test
    fun closed_shows_compare_and_report_without_disputes() {
        val state = PropertyRoleUi.resolve(
            property(PropertyStatus.CLOSED, moveInSubmitted = true, moveOutSubmitted = true),
            tenantId,
            listOf(sig(landlordId), sig(tenantId)),
            listOf(sig(landlordId, InspectionPhase.MOVE_OUT), sig(tenantId, InspectionPhase.MOVE_OUT)),
        )!!
        assertEquals(
            setOf(PropertyPrimaryAction.VIEW_COMPARE, PropertyPrimaryAction.VIEW_REPORT),
            state.actions,
        )
    }

    @Test
    fun closed_shows_dispute_history_when_disputes_exist() {
        val state = PropertyRoleUi.resolve(
            property(PropertyStatus.CLOSED, moveInSubmitted = true, moveOutSubmitted = true),
            tenantId,
            listOf(sig(landlordId), sig(tenantId)),
            listOf(sig(landlordId, InspectionPhase.MOVE_OUT), sig(tenantId, InspectionPhase.MOVE_OUT)),
            hasDisputes = true,
        )!!
        assertTrue(PropertyPrimaryAction.VIEW_DISPUTES in state.actions)
    }
}
