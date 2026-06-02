package com.example.tenant_landlorddisputedocumenter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus

@Entity(
    tableName = "properties",
    indices = [
        Index(value = ["landlordId"]),
        Index(value = ["tenantId"]),
        Index(value = ["inviteCode"], unique = true),
    ],
)
data class PropertyEntity(
    @PrimaryKey val id: String,
    val landlordId: String,
    val tenantId: String?,
    val address: String,
    val rent: Double,
    val deposit: Double,
    val leaseStartMillis: Long,
    val leaseEndMillis: Long,
    val inviteCode: String,
    val status: PropertyStatus,
    val moveInInspectionSubmittedAtMillis: Long?,
    val moveOutInspectionSubmittedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain(): Property = Property(
        id = id,
        landlordId = landlordId,
        tenantId = tenantId,
        address = address,
        rent = rent,
        deposit = deposit,
        leaseStartMillis = leaseStartMillis,
        leaseEndMillis = leaseEndMillis,
        inviteCode = inviteCode,
        status = status,
        moveInInspectionSubmittedAtMillis = moveInInspectionSubmittedAtMillis,
        moveOutInspectionSubmittedAtMillis = moveOutInspectionSubmittedAtMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    companion object {
        fun from(p: Property): PropertyEntity = PropertyEntity(
            id = p.id,
            landlordId = p.landlordId,
            tenantId = p.tenantId,
            address = p.address,
            rent = p.rent,
            deposit = p.deposit,
            leaseStartMillis = p.leaseStartMillis,
            leaseEndMillis = p.leaseEndMillis,
            inviteCode = p.inviteCode,
            status = p.status,
            moveInInspectionSubmittedAtMillis = p.moveInInspectionSubmittedAtMillis,
            moveOutInspectionSubmittedAtMillis = p.moveOutInspectionSubmittedAtMillis,
            createdAtMillis = p.createdAtMillis,
            updatedAtMillis = p.updatedAtMillis,
        )
    }
}
