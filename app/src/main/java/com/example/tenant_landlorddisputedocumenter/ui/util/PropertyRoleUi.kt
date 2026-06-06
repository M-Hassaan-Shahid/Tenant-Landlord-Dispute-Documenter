package com.example.tenant_landlorddisputedocumenter.ui.util

import androidx.annotation.StringRes
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.Signature

/** Primary actions shown on the property details screen for the current user. */
enum class PropertyPrimaryAction {
    ROOM_SETUP,
    DOCUMENT_MOVE_IN,
    DOCUMENT_MOVE_OUT,
    REVIEW_SIGN_MOVE_IN,
    REVIEW_SIGN_MOVE_OUT,
    START_MOVE_OUT,
    WAITING_LANDLORD_DOCUMENT,
    VIEW_COMPARE,
    VIEW_REPORT,
    VIEW_DISPUTES,
}

data class PropertyRoleState(
    val isLandlord: Boolean,
    val isTenant: Boolean,
    @StringRes val roleBannerTitleRes: Int,
    @StringRes val roleBannerBodyRes: Int,
    val actions: Set<PropertyPrimaryAction>,
    @StringRes val waitingCardTitleRes: Int? = null,
    @StringRes val waitingCardBodyRes: Int? = null,
)

object PropertyRoleUi {

    fun resolve(
        property: Property,
        currentUid: String?,
        moveInSignatures: List<Signature>,
        moveOutSignatures: List<Signature>,
        hasRooms: Boolean = false,
        hasDisputes: Boolean = false,
    ): PropertyRoleState? {
        if (currentUid == null) return null
        val isLandlord = property.landlordId == currentUid
        val isTenant = property.tenantId == currentUid
        if (!isLandlord && !isTenant) return null

        val moveInSubmitted = property.moveInInspectionSubmittedAtMillis != null
        val moveOutSubmitted = property.moveOutInspectionSubmittedAtMillis != null
        val signedMoveIn = moveInSignatures.any { it.signerUid == currentUid }
        val signedMoveOut = moveOutSignatures.any { it.signerUid == currentUid }

        val actions = linkedSetOf<PropertyPrimaryAction>()
        var waitingTitle: Int? = null
        var waitingBody: Int? = null
        val (titleRes, bodyRes) = when (property.status) {
            PropertyStatus.PENDING_APPROVAL -> if (isLandlord) {
                R.string.role_banner_landlord_approve_title to R.string.role_banner_landlord_approve_body
            } else {
                R.string.role_banner_tenant_pending_title to R.string.role_banner_tenant_pending_body
            }

            PropertyStatus.ACTIVE -> when {
                isLandlord && signedMoveIn -> {
                    actions += PropertyPrimaryAction.VIEW_DISPUTES
                    R.string.role_banner_landlord_waiting_tenant_title to
                        R.string.role_banner_landlord_waiting_tenant_body
                }
                isLandlord && moveInSubmitted && !signedMoveIn -> {
                    actions += PropertyPrimaryAction.REVIEW_SIGN_MOVE_IN
                    actions += PropertyPrimaryAction.VIEW_DISPUTES
                    R.string.role_banner_landlord_sign_title to R.string.role_banner_landlord_sign_body
                }
                isLandlord && !moveInSubmitted -> {
                    actions += PropertyPrimaryAction.ROOM_SETUP
                    actions += PropertyPrimaryAction.DOCUMENT_MOVE_IN
                    if (hasRooms) {
                        R.string.role_banner_landlord_active_title to R.string.flow_move_in_landlord
                    } else {
                        R.string.role_banner_landlord_active_title to R.string.role_banner_landlord_active_body
                    }
                }
                isTenant && signedMoveIn -> {
                    R.string.role_banner_tenant_waiting_landlord_sign_title to
                        R.string.role_banner_tenant_waiting_landlord_sign_body
                }
                isTenant && moveInSubmitted && !signedMoveIn -> {
                    actions += PropertyPrimaryAction.REVIEW_SIGN_MOVE_IN
                    actions += PropertyPrimaryAction.VIEW_DISPUTES
                    R.string.role_banner_tenant_review_title to R.string.flow_move_in_tenant
                }
                isTenant && !moveInSubmitted -> {
                    actions += PropertyPrimaryAction.WAITING_LANDLORD_DOCUMENT
                    if (hasRooms) {
                        waitingTitle = R.string.waiting_landlord_documenting_title
                        waitingBody = R.string.waiting_landlord_documenting_body
                        R.string.role_banner_tenant_waiting_doc_title to
                            R.string.waiting_landlord_documenting_body
                    } else {
                        waitingTitle = R.string.waiting_landlord_setup_title
                        waitingBody = R.string.waiting_landlord_setup_body
                        R.string.role_banner_tenant_waiting_doc_title to
                            R.string.waiting_landlord_setup_body
                    }
                }
                else -> R.string.role_banner_landlord_active_title to R.string.role_banner_landlord_active_body
            }

            PropertyStatus.OCCUPIED -> if (isLandlord) {
                actions += PropertyPrimaryAction.START_MOVE_OUT
                R.string.role_banner_landlord_occupied_title to R.string.role_banner_landlord_occupied_body
            } else {
                R.string.role_banner_tenant_occupied_title to R.string.role_banner_tenant_occupied_body
            }

            PropertyStatus.MOVE_OUT -> when {
                isLandlord && signedMoveOut -> {
                    actions += PropertyPrimaryAction.VIEW_DISPUTES
                    actions += PropertyPrimaryAction.VIEW_COMPARE
                    actions += PropertyPrimaryAction.VIEW_REPORT
                    R.string.role_banner_landlord_waiting_tenant_move_out_title to
                        R.string.role_banner_landlord_waiting_tenant_move_out_body
                }
                isLandlord && moveOutSubmitted && !signedMoveOut -> {
                    actions += PropertyPrimaryAction.REVIEW_SIGN_MOVE_OUT
                    actions += PropertyPrimaryAction.VIEW_DISPUTES
                    actions += PropertyPrimaryAction.VIEW_COMPARE
                    actions += PropertyPrimaryAction.VIEW_REPORT
                    R.string.role_banner_landlord_sign_move_out_title to
                        R.string.role_banner_landlord_sign_move_out_body
                }
                isLandlord && !moveOutSubmitted -> {
                    actions += PropertyPrimaryAction.DOCUMENT_MOVE_OUT
                    R.string.role_banner_landlord_move_out_title to R.string.flow_move_out_landlord
                }
                isTenant && signedMoveOut -> {
                    actions += PropertyPrimaryAction.VIEW_COMPARE
                    actions += PropertyPrimaryAction.VIEW_REPORT
                    actions += PropertyPrimaryAction.VIEW_DISPUTES
                    R.string.role_banner_tenant_waiting_landlord_move_out_title to
                        R.string.role_banner_tenant_waiting_landlord_move_out_body
                }
                isTenant && moveOutSubmitted && !signedMoveOut -> {
                    actions += PropertyPrimaryAction.REVIEW_SIGN_MOVE_OUT
                    actions += PropertyPrimaryAction.VIEW_DISPUTES
                    actions += PropertyPrimaryAction.VIEW_COMPARE
                    actions += PropertyPrimaryAction.VIEW_REPORT
                    R.string.role_banner_tenant_review_move_out_title to R.string.flow_move_out_tenant_review
                }
                isTenant && !moveOutSubmitted -> {
                    actions += PropertyPrimaryAction.WAITING_LANDLORD_DOCUMENT
                    waitingTitle = R.string.waiting_landlord_move_out_title
                    waitingBody = R.string.waiting_landlord_move_out_body
                    R.string.role_banner_tenant_move_out_title to R.string.flow_move_out_tenant_waiting
                }
                else -> R.string.role_banner_landlord_move_out_title to R.string.flow_move_out_landlord
            }

            PropertyStatus.CLOSED -> {
                actions += PropertyPrimaryAction.VIEW_COMPARE
                actions += PropertyPrimaryAction.VIEW_REPORT
                if (hasDisputes) {
                    actions += PropertyPrimaryAction.VIEW_DISPUTES
                }
                R.string.role_banner_closed_title to R.string.role_banner_closed_body
            }

            PropertyStatus.PENDING -> if (isLandlord) {
                R.string.role_banner_landlord_pending_tenant_title to R.string.role_banner_landlord_pending_tenant_body
            } else {
                R.string.role_banner_tenant_pending_title to R.string.role_banner_tenant_pending_body
            }

            PropertyStatus.REJECTED -> R.string.role_banner_rejected_title to R.string.role_banner_rejected_body
        }

        return PropertyRoleState(
            isLandlord = isLandlord,
            isTenant = isTenant,
            roleBannerTitleRes = titleRes,
            roleBannerBodyRes = bodyRes,
            actions = actions,
            waitingCardTitleRes = waitingTitle,
            waitingCardBodyRes = waitingBody,
        )
    }

    fun dashboardEmptySubtitleRes(isLandlord: Boolean): Int =
        if (isLandlord) R.string.dashboard_empty_subtitle_landlord else R.string.dashboard_empty_subtitle_tenant

    fun dashboardFabActionRes(isLandlord: Boolean): Int =
        if (isLandlord) R.string.create_property_landlord else R.string.join_property_tenant

    fun propertyCardHint(property: Property, currentUid: String): String = when {
        property.landlordId == currentUid -> landlordCardHint(property)
        property.tenantId == currentUid -> tenantCardHint(property)
        else -> ""
    }

    private fun landlordCardHint(property: Property): String = when (property.status) {
        PropertyStatus.PENDING -> " · Share invite code"
        PropertyStatus.PENDING_APPROVAL -> " · Approve tenant"
        PropertyStatus.ACTIVE ->
            if (property.moveInInspectionSubmittedAtMillis == null) " · Set up & document move-in"
            else " · Sign move-in record"
        PropertyStatus.OCCUPIED -> " · Start move-out when lease ends"
        PropertyStatus.MOVE_OUT ->
            if (property.moveOutInspectionSubmittedAtMillis == null) " · Document move-out"
            else " · Sign move-out record"
        PropertyStatus.CLOSED -> " · Record closed"
        PropertyStatus.REJECTED -> " · Tenant rejected"
    }

    private fun tenantCardHint(property: Property): String = when (property.status) {
        PropertyStatus.PENDING_APPROVAL -> " · Awaiting approval"
        PropertyStatus.ACTIVE ->
            if (property.moveInInspectionSubmittedAtMillis == null) " · Waiting for landlord"
            else " · Review & sign move-in"
        PropertyStatus.OCCUPIED -> " · Lease active"
        PropertyStatus.MOVE_OUT ->
            if (property.moveOutInspectionSubmittedAtMillis == null) " · Waiting for landlord"
            else " · Review & sign move-out"
        PropertyStatus.CLOSED -> " · Record closed"
        else -> ""
    }
}
