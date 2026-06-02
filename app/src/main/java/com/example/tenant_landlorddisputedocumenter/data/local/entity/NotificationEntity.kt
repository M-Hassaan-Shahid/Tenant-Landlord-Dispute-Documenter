package com.example.tenant_landlorddisputedocumenter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tenant_landlorddisputedocumenter.domain.model.AppNotification
import com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["recipientUid"]), Index(value = ["createdAtMillis"])],
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val recipientUid: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val propertyId: String?,
    val createdAtMillis: Long,
    val read: Boolean,
) {
    fun toDomain(): AppNotification = AppNotification(
        id, recipientUid, type, title, body, propertyId, createdAtMillis, read,
    )

    companion object {
        fun from(n: AppNotification): NotificationEntity = NotificationEntity(
            n.id, n.recipientUid, n.type, n.title, n.body, n.propertyId, n.createdAtMillis, n.read,
        )
    }
}
