package com.example.tenant_landlorddisputedocumenter.domain.model

/** Validates landlord inspection submission preconditions (pure logic for tests). */
object InspectionFinishPolicy {

    data class Validation(val ok: Boolean, val error: String? = null)

    fun validate(
        phase: InspectionPhase,
        roomCount: Int,
        items: List<ChecklistItem>,
        hasUploadedPhasePhoto: Boolean,
    ): Validation {
        if (roomCount == 0) {
            return Validation(false, "Add at least one room in Room Setup before inspecting.")
        }
        if (items.isEmpty()) {
            return Validation(false, "Add at least one checklist item before finishing.")
        }
        val unrated = items.count { item ->
            if (phase == InspectionPhase.MOVE_IN) item.moveInRating == null else item.moveOutRating == null
        }
        if (unrated > 0) {
            return Validation(
                false,
                "Rate every checklist item before finishing ($unrated remaining).",
            )
        }
        val hasPhasePhoto = items.any { item ->
            if (phase == InspectionPhase.MOVE_IN) item.moveInPhotoIds.isNotEmpty()
            else item.moveOutPhotoIds.isNotEmpty()
        }
        if (!hasPhasePhoto) {
            return Validation(false, "Capture at least one photo for this inspection before finishing.")
        }
        if (!hasUploadedPhasePhoto) {
            return Validation(
                false,
                "Wait for photos to finish uploading before submitting the inspection.",
            )
        }
        return Validation(true)
    }
}
