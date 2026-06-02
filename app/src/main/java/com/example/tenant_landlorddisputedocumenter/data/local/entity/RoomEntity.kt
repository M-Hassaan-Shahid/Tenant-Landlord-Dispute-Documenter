package com.example.tenant_landlorddisputedocumenter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionRoom

@Entity(
    tableName = "rooms",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["propertyId"])],
)
data class RoomEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val name: String,
    val sortOrder: Int,
) {
    fun toDomain(): InspectionRoom = InspectionRoom(id, propertyId, name, sortOrder)

    companion object {
        fun from(room: InspectionRoom): RoomEntity =
            RoomEntity(room.id, room.propertyId, room.name, room.sortOrder)
    }
}
