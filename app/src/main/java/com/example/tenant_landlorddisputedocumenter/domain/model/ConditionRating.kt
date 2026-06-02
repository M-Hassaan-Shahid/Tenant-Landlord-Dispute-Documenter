package com.example.tenant_landlorddisputedocumenter.domain.model

/** Condition rating for a checklist item. Ordered worst-to-best so comparisons are arithmetic. */
enum class ConditionRating(val displayLabel: String, val score: Int) {
    DAMAGED("Damaged", 0),
    FAIR("Fair", 1),
    GOOD("Good", 2);

    companion object {
        fun from(raw: String?): ConditionRating? = values().firstOrNull { it.name == raw?.uppercase() }
    }
}

/** Result of comparing a move-in rating against a move-out rating. */
enum class RatingDelta {
    UNCHANGED,
    IMPROVED,
    DEGRADED;

    companion object {
        fun compute(moveIn: ConditionRating?, moveOut: ConditionRating?): RatingDelta {
            if (moveIn == null || moveOut == null) return UNCHANGED
            return when {
                moveOut.score == moveIn.score -> UNCHANGED
                moveOut.score > moveIn.score -> IMPROVED
                else -> DEGRADED
            }
        }
    }
}
