package com.example.tenant_landlorddisputedocumenter.domain.model

/**
 * A room within a property (kitchen, bathroom, etc.).
 *
 * Named [InspectionRoom] rather than `Room` to avoid clashing with [androidx.room.Room].
 */
data class InspectionRoom(
    val id: String,
    val propertyId: String,
    val name: String,
    val sortOrder: Int = 0,
)
