package com.example.tenant_landlorddisputedocumenter.navigation

import android.os.Bundle
import androidx.core.os.bundleOf
import com.example.tenant_landlorddisputedocumenter.R

/** Type-safe navigation args (manual Safe Args equivalent for AGP 9). */

data class PropertyDetailsFragmentArgs(val propertyId: String) {
    fun toBundle(): Bundle = bundleOf("propertyId" to propertyId)
    companion object {
        fun fromBundle(bundle: Bundle) = PropertyDetailsFragmentArgs(
            requireNotNull(bundle.getString("propertyId")) { "propertyId is required" },
        )
    }
}

data class RoomSetupFragmentArgs(val propertyId: String) {
    companion object {
        fun fromBundle(bundle: Bundle) = RoomSetupFragmentArgs(
            requireNotNull(bundle.getString("propertyId")) { "propertyId is required" },
        )
    }
}

data class InspectionFragmentArgs(val propertyId: String, val phase: String) {
    companion object {
        fun fromBundle(bundle: Bundle) = InspectionFragmentArgs(
            requireNotNull(bundle.getString("propertyId")) { "propertyId is required" },
            bundle.getString("phase") ?: "MOVE_IN",
        )
    }
}

data class ReviewSignFragmentArgs(val propertyId: String, val phase: String) {
    companion object {
        fun fromBundle(bundle: Bundle) = ReviewSignFragmentArgs(
            requireNotNull(bundle.getString("propertyId")) { "propertyId is required" },
            bundle.getString("phase") ?: "MOVE_IN",
        )
    }
}

data class CompareFragmentArgs(val propertyId: String) {
    companion object {
        fun fromBundle(bundle: Bundle) = CompareFragmentArgs(
            requireNotNull(bundle.getString("propertyId")) { "propertyId is required" },
        )
    }
}

data class DisputeFragmentArgs(val propertyId: String, val itemId: String, val itemName: String) {
    companion object {
        fun fromBundle(bundle: Bundle) = DisputeFragmentArgs(
            requireNotNull(bundle.getString("propertyId")) { "propertyId is required" },
            requireNotNull(bundle.getString("itemId")) { "itemId is required" },
            bundle.getString("itemName") ?: "Item",
        )
    }
}

data class ReportFragmentArgs(val propertyId: String) {
    companion object {
        fun fromBundle(bundle: Bundle) = ReportFragmentArgs(
            requireNotNull(bundle.getString("propertyId")) { "propertyId is required" },
        )
    }
}

data class DisputesListFragmentArgs(val propertyId: String) {
    companion object {
        fun fromBundle(bundle: Bundle) = DisputesListFragmentArgs(
            requireNotNull(bundle.getString("propertyId")) { "propertyId is required" },
        )
    }
}

object DashboardFragmentDirections {
    fun actionDashboardToPropertyDetails(propertyId: String) = SimpleNavDirections(
        R.id.action_dashboard_to_property_details,
        PropertyDetailsFragmentArgs(propertyId).toBundle(),
    )
}

object PropertyDetailsFragmentDirections {
    fun actionPropertyDetailsToRoomSetup(propertyId: String) = SimpleNavDirections(
        R.id.action_property_details_to_room_setup,
        bundleOf("propertyId" to propertyId),
    )

    fun actionPropertyDetailsToInspection(propertyId: String, phase: String) = SimpleNavDirections(
        R.id.action_property_details_to_inspection,
        bundleOf("propertyId" to propertyId, "phase" to phase),
    )

    fun actionPropertyDetailsToCompare(propertyId: String) = SimpleNavDirections(
        R.id.action_property_details_to_compare,
        bundleOf("propertyId" to propertyId),
    )

    fun actionPropertyDetailsToReport(propertyId: String) = SimpleNavDirections(
        R.id.action_property_details_to_report,
        bundleOf("propertyId" to propertyId),
    )

    fun actionPropertyDetailsToDisputesList(propertyId: String) = SimpleNavDirections(
        R.id.action_property_details_to_disputes_list,
        bundleOf("propertyId" to propertyId),
    )
}

object InspectionFragmentDirections {
    fun actionInspectionToReviewSign(propertyId: String, phase: String) = SimpleNavDirections(
        R.id.action_inspection_to_review_sign,
        bundleOf("propertyId" to propertyId, "phase" to phase),
    )
}

object CompareFragmentDirections {
    fun actionCompareToDispute(propertyId: String, itemId: String, itemName: String) = SimpleNavDirections(
        R.id.action_compare_to_dispute,
        bundleOf("propertyId" to propertyId, "itemId" to itemId, "itemName" to itemName),
    )

    fun actionCompareToReport(propertyId: String) = SimpleNavDirections(
        R.id.action_compare_to_report,
        bundleOf("propertyId" to propertyId),
    )
}
