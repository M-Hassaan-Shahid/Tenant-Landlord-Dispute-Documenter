package com.example.tenant_landlorddisputedocumenter.domain.model

/** Lifecycle stages of a property between move-in and move-out. */
enum class PropertyStatus {
    /** Created by landlord; awaiting tenant join. */
    PENDING,

    /** Tenant joined and is waiting for the landlord to approve. */
    PENDING_APPROVAL,

    /** Tenant joined and approved by landlord; can run move-in inspection. */
    ACTIVE,

    /** Tenant request was explicitly rejected by landlord; tenant link cleared. */
    REJECTED,

    /** Move-in inspection complete and signed by both parties. */
    OCCUPIED,

    /** Move-out inspection in progress. */
    MOVE_OUT,

    /** Move-out signed; record is locked. */
    CLOSED;

    /** True once an inspection has begun (room/checklist structure should lock from here). */
    val inspectionStarted: Boolean
        get() = this == OCCUPIED || this == MOVE_OUT || this == CLOSED

    /** Human-friendly label for chips. */
    val label: String
        get() = when (this) {
            PENDING -> "Awaiting tenant"
            PENDING_APPROVAL -> "Awaiting approval"
            ACTIVE -> "Active"
            REJECTED -> "Rejected"
            OCCUPIED -> "Occupied"
            MOVE_OUT -> "Move-out"
            CLOSED -> "Closed"
        }

    companion object {
        fun from(raw: String?): PropertyStatus = values().firstOrNull { it.name == raw?.uppercase() } ?: PENDING
    }
}
