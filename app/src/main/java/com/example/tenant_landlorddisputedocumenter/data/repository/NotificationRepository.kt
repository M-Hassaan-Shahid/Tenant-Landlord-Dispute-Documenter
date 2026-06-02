package com.example.tenant_landlorddisputedocumenter.data.repository

import com.example.tenant_landlorddisputedocumenter.data.SyncResult
import com.example.tenant_landlorddisputedocumenter.data.local.dao.NotificationDao
import com.example.tenant_landlorddisputedocumenter.data.local.entity.NotificationEntity
import com.example.tenant_landlorddisputedocumenter.data.remote.FirestorePaths
import com.example.tenant_landlorddisputedocumenter.data.remote.firestoreWrite
import com.example.tenant_landlorddisputedocumenter.domain.model.AppNotification
import com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.util.Ids
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NotificationRepository(
    private val notificationDao: NotificationDao,
    private val firestore: FirebaseFirestore,
    private val currentUserId: () -> String?,
) {
    fun observeForUser(uid: String): Flow<List<AppNotification>> =
        notificationDao.observeForUser(uid).map { list -> list.map { it.toDomain() } }

    fun observeUnreadCount(uid: String): Flow<Int> = notificationDao.observeUnreadCount(uid)

    suspend fun push(
        recipientUid: String,
        type: NotificationType,
        title: String,
        body: String,
        propertyId: String? = null,
    ) {
        val notification = AppNotification(
            id = Ids.newId(),
            recipientUid = recipientUid,
            type = type,
            title = title,
            body = body,
            propertyId = propertyId,
        )
        pushNotification(notification)
        if (recipientUid == currentUserId()) {
            notificationDao.upsert(NotificationEntity.from(notification))
        }
    }

    /** Persist a notification already written to Firestore (e.g. after pull sync). */
    suspend fun upsertLocal(notification: AppNotification) {
        if (notification.recipientUid == currentUserId()) {
            notificationDao.upsert(NotificationEntity.from(notification))
        }
    }

    suspend fun markRead(id: String) {
        notificationDao.markRead(id)
        runCatching {
            firestore.collection(FirestorePaths.NOTIFICATIONS).document(id)
                .update("read", true).await()
        }
    }
    
    suspend fun markAllRead(uid: String): Outcome<Unit> = runCatching {
        val unread = firestore.collection(FirestorePaths.NOTIFICATIONS)
            .whereEqualTo("recipientUid", uid)
            .whereEqualTo("read", false)
            .get().await()
        unread.documents.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.update(doc.reference, "read", true) }
            batch.commit().await()
        }
        notificationDao.markAllRead(uid)
        Unit
    }.fold(
        onSuccess = { Outcome.Success(it) },
        onFailure = {
            Outcome.Failure(it, it.localizedMessage ?: "Could not mark notifications read in cloud.")
        },
    )

    suspend fun ensureLeaseEndingReminders(
        uid: String,
        properties: List<Property>,
        withinDays: Int = 14,
    ) {
        val now = System.currentTimeMillis()
        val window = TimeUnit.DAYS.toMillis(withinDays.toLong())
        for (property in properties) {
            val remaining = property.leaseEndMillis - now
            if (remaining in 0..window) {
                val existing = notificationDao.countForProperty(
                    uid, property.id, NotificationType.LEASE_ENDING,
                )
                if (existing > 0) continue
                val days = TimeUnit.MILLISECONDS.toDays(remaining).toInt()
                push(
                    recipientUid = uid,
                    type = NotificationType.LEASE_ENDING,
                    title = "Lease ending soon",
                    body = if (days <= 0) "${property.address} — lease ends today."
                    else "${property.address} — $days day(s) until lease end.",
                    propertyId = property.id,
                )
            }
        }
    }

    suspend fun syncForUser(uid: String): SyncResult = runCatching {
        notificationDao.deleteNotForRecipient(uid)
        val remote = firestore.collection(FirestorePaths.NOTIFICATIONS)
            .whereEqualTo("recipientUid", uid).get().await()
        val notifications = remote.map { it.toNotification() }
        val remoteIds = notifications.map { it.id }.toSet()
        notificationDao.listIdsForUser(uid)
            .filter { it !in remoteIds }
            .forEach { notificationDao.delete(it) }
        notificationDao.upsertAll(notifications.map(NotificationEntity::from))
    }.fold(
        onSuccess = { SyncResult.ok() },
        onFailure = { SyncResult.from("notifications", it) },
    )

    private suspend fun pushNotification(notification: AppNotification) {
        firestoreWrite("notification") {
            firestore.collection(FirestorePaths.NOTIFICATIONS).document(notification.id)
                .set(notification.toFirestoreMap()).await()
        }
    }

    private fun AppNotification.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "recipientUid" to recipientUid,
        "type" to type.name,
        "title" to title,
        "body" to body,
        "propertyId" to propertyId,
        "createdAtMillis" to createdAtMillis,
        "read" to read,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toNotification(): AppNotification = AppNotification(
        id = getString("id") ?: id,
        recipientUid = getString("recipientUid").orEmpty(),
        type = NotificationType.from(getString("type")),
        title = getString("title").orEmpty(),
        body = getString("body").orEmpty(),
        propertyId = getString("propertyId"),
        createdAtMillis = getLong("createdAtMillis") ?: System.currentTimeMillis(),
        read = getBoolean("read") ?: false,
    )
}
