package com.example.tenant_landlorddisputedocumenter.domain

import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import com.example.tenant_landlorddisputedocumenter.domain.model.RatingDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class RatingDeltaTest {

    @Test
    fun degraded_when_moveOut_worse_than_moveIn() {
        val delta = RatingDelta.compute(ConditionRating.GOOD, ConditionRating.DAMAGED)
        assertEquals(RatingDelta.DEGRADED, delta)
    }

    @Test
    fun improved_when_moveOut_better_than_moveIn() {
        val delta = RatingDelta.compute(ConditionRating.DAMAGED, ConditionRating.GOOD)
        assertEquals(RatingDelta.IMPROVED, delta)
    }

    @Test
    fun unchanged_when_ratings_match() {
        val delta = RatingDelta.compute(ConditionRating.FAIR, ConditionRating.FAIR)
        assertEquals(RatingDelta.UNCHANGED, delta)
    }
}
