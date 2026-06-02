package com.example.tenant_landlorddisputedocumenter.domain.model

/**
 * A single thing being inspected inside a [InspectionRoom] — e.g. "walls", "ceiling fan", "main door".
 *
 * The item stores both move-in and move-out state side-by-side so the comparison screen is a single read.
 */
data class ChecklistItem(
    val id: String,
    val roomId: String,
    val propertyId: String,
    val name: String,

    val moveInNote: String = "",
    val moveInRating: ConditionRating? = null,
    val moveInTimestamp: Long? = null,
    val moveInPhotoIds: List<String> = emptyList(),

    val moveOutNote: String = "",
    val moveOutRating: ConditionRating? = null,
    val moveOutTimestamp: Long? = null,
    val moveOutPhotoIds: List<String> = emptyList(),
) {
    fun ratingDelta(): RatingDelta = RatingDelta.compute(moveInRating, moveOutRating)

    fun hasPhaseProgress(phase: InspectionPhase): Boolean = when (phase) {
        InspectionPhase.MOVE_IN -> moveInRating != null ||
            moveInPhotoIds.isNotEmpty() ||
            moveInNote.isNotBlank()
        InspectionPhase.MOVE_OUT -> moveOutRating != null ||
            moveOutPhotoIds.isNotEmpty() ||
            moveOutNote.isNotBlank()
    }
}
