package com.example.tenant_landlorddisputedocumenter.domain.model

/** Which leg of the inspection a piece of evidence belongs to. */
enum class InspectionPhase {
    MOVE_IN,
    MOVE_OUT;

    companion object {
        fun from(raw: String?): InspectionPhase =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MOVE_IN
    }
}
