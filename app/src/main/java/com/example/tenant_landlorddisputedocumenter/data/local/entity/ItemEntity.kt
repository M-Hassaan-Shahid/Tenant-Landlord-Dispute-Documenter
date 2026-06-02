package com.example.tenant_landlorddisputedocumenter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["roomId"]), Index(value = ["propertyId"])],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val propertyId: String,
    val name: String,

    val moveInNote: String,
    val moveInRating: ConditionRating?,
    val moveInTimestamp: Long?,
    val moveInPhotoIds: List<String>,

    val moveOutNote: String,
    val moveOutRating: ConditionRating?,
    val moveOutTimestamp: Long?,
    val moveOutPhotoIds: List<String>,
) {
    fun toDomain(): ChecklistItem = ChecklistItem(
        id, roomId, propertyId, name,
        moveInNote, moveInRating, moveInTimestamp, moveInPhotoIds,
        moveOutNote, moveOutRating, moveOutTimestamp, moveOutPhotoIds,
    )

    companion object {
        fun from(i: ChecklistItem): ItemEntity = ItemEntity(
            id = i.id,
            roomId = i.roomId,
            propertyId = i.propertyId,
            name = i.name,
            moveInNote = i.moveInNote,
            moveInRating = i.moveInRating,
            moveInTimestamp = i.moveInTimestamp,
            moveInPhotoIds = i.moveInPhotoIds,
            moveOutNote = i.moveOutNote,
            moveOutRating = i.moveOutRating,
            moveOutTimestamp = i.moveOutTimestamp,
            moveOutPhotoIds = i.moveOutPhotoIds,
        )
    }
}
