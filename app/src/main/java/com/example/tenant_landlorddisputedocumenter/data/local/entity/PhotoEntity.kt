package com.example.tenant_landlorddisputedocumenter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Photo

@Entity(
    tableName = "photos",
    indices = [Index(value = ["itemId"]), Index(value = ["propertyId"])],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val propertyId: String,
    val phase: InspectionPhase,
    val capturedByUid: String,
    val capturedAtMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val localUri: String?,
    val remoteUrl: String?,
    val uploaded: Boolean,
) {
    fun toDomain(): Photo = Photo(
        id, itemId, propertyId, phase, capturedByUid, capturedAtMillis,
        latitude, longitude, localUri, remoteUrl, uploaded,
    )

    companion object {
        fun from(p: Photo): PhotoEntity = PhotoEntity(
            id = p.id,
            itemId = p.itemId,
            propertyId = p.propertyId,
            phase = p.phase,
            capturedByUid = p.capturedByUid,
            capturedAtMillis = p.capturedAtMillis,
            latitude = p.latitude,
            longitude = p.longitude,
            localUri = p.localUri,
            remoteUrl = p.remoteUrl,
            uploaded = p.uploaded,
        )
    }
}
