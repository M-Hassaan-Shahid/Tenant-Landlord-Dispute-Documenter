package com.example.tenant_landlorddisputedocumenter.domain

import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionFinishPolicy
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionFinishPolicyTest {

    private fun ratedItem(withPhoto: Boolean = true) = ChecklistItem(
        id = "item-1",
        roomId = "room-1",
        propertyId = "p1",
        name = "Wall",
        moveInRating = ConditionRating.GOOD,
        moveInPhotoIds = if (withPhoto) listOf("photo-1") else emptyList(),
    )

    @Test
    fun validate_requiresUploadedPhotos() {
        val result = InspectionFinishPolicy.validate(
            phase = InspectionPhase.MOVE_IN,
            roomCount = 1,
            items = listOf(ratedItem()),
            hasUploadedPhasePhoto = false,
        )
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("upload"))
    }

    @Test
    fun validate_passesWhenComplete() {
        val result = InspectionFinishPolicy.validate(
            phase = InspectionPhase.MOVE_IN,
            roomCount = 1,
            items = listOf(ratedItem()),
            hasUploadedPhasePhoto = true,
        )
        assertTrue(result.ok)
    }

    @Test
    fun validate_blocksUnratedItems() {
        val unrated = ratedItem().copy(moveInRating = null)
        val result = InspectionFinishPolicy.validate(
            phase = InspectionPhase.MOVE_IN,
            roomCount = 1,
            items = listOf(unrated),
            hasUploadedPhasePhoto = true,
        )
        assertFalse(result.ok)
    }
}
