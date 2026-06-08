package com.example.tenant_landlorddisputedocumenter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole

@Entity(
    tableName = "disputes",
    indices = [Index(value = ["propertyId"]), Index(value = ["itemId"])],
)
data class DisputeEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val itemId: String,
    val raisedByUid: String,
    val raisedByRole: UserRole,
    val reason: String,
    val counterPhotoIds: List<String>,
    val counterNote: String,
    val resolutionNote: String,
    val proposedByUid: String = "",
    val tenantResponseNote: String = "",
    val status: DisputeStatus,
    val raisedAtMillis: Long,
    val resolvedAtMillis: Long?,
) {
    fun toDomain(): Dispute = Dispute(
        id = id,
        propertyId = propertyId,
        itemId = itemId,
        raisedByUid = raisedByUid,
        raisedByRole = raisedByRole,
        reason = reason,
        counterPhotoIds = counterPhotoIds,
        counterNote = counterNote,
        resolutionNote = resolutionNote,
        proposedByUid = proposedByUid,
        tenantResponseNote = tenantResponseNote,
        status = status,
        raisedAtMillis = raisedAtMillis,
        resolvedAtMillis = resolvedAtMillis,
    )

    companion object {
        fun from(d: Dispute): DisputeEntity = DisputeEntity(
            id = d.id,
            propertyId = d.propertyId,
            itemId = d.itemId,
            raisedByUid = d.raisedByUid,
            raisedByRole = d.raisedByRole,
            reason = d.reason,
            counterPhotoIds = d.counterPhotoIds,
            counterNote = d.counterNote,
            resolutionNote = d.resolutionNote,
            proposedByUid = d.proposedByUid,
            tenantResponseNote = d.tenantResponseNote,
            status = d.status,
            raisedAtMillis = d.raisedAtMillis,
            resolvedAtMillis = d.resolvedAtMillis,
        )
    }
}
