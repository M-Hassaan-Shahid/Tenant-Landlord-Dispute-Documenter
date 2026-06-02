package com.example.tenant_landlorddisputedocumenter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tenant_landlorddisputedocumenter.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notifications: List<NotificationEntity>)

    @Query("SELECT * FROM notifications WHERE recipientUid = :uid ORDER BY createdAtMillis DESC")
    fun observeForUser(uid: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE recipientUid = :uid AND read = 0")
    fun observeUnreadCount(uid: String): Flow<Int>

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET read = 1 WHERE recipientUid = :uid")
    suspend fun markAllRead(uid: String)

    @Query(
        """
        SELECT COUNT(*) FROM notifications
        WHERE recipientUid = :uid AND propertyId = :propertyId AND type = :type
        """
    )
    suspend fun countForProperty(
        uid: String,
        propertyId: String,
        type: com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType,
    ): Int

    @Query("DELETE FROM notifications WHERE recipientUid != :uid")
    suspend fun deleteNotForRecipient(uid: String)

    @Query("SELECT id FROM notifications WHERE recipientUid = :uid")
    suspend fun listIdsForUser(uid: String): List<String>

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun delete(id: String)
}
