package com.example.tenant_landlorddisputedocumenter.data

import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** Pulls remote Firebase data into Room and schedules local lease reminders. */
class SyncCoordinator(private val container: ServiceContainer) {

    suspend fun syncAllForUser(uid: String): SyncResult {
        var result = SyncResult.ok()
        result = result.merge(
            SyncResult.from("FCM token", runCatching { registerFcmToken(uid) }.exceptionOrNull()),
        )
        result = result.merge(
            SyncResult.from("profile", runCatching {
                container.authRepository.refreshProfile(uid)
            }.exceptionOrNull()),
        )
        result = result.merge(
            SyncResult.from("properties", runCatching {
                container.propertyRepository.syncForUser(uid)
            }.exceptionOrNull()),
        )
        result = result.merge(container.notificationRepository.syncForUser(uid))

        val properties = container.propertyRepository.observeForUser(uid).first()
        for (property in properties) {
            result = result.merge(container.inspectionRepository.syncForProperty(property.id))
            result = result.merge(container.disputeRepository.syncForProperty(property.id))
        }

        result = result.merge(
            SyncResult.from("photo uploads", runCatching {
                container.inspectionRepository.syncPendingUploads()
            }.exceptionOrNull()),
        )
        result = result.merge(
            SyncResult.from("lease reminders", runCatching {
                container.notificationRepository.ensureLeaseEndingReminders(uid, properties)
            }.exceptionOrNull()),
        )
        return result
    }

    /** Pulls latest property, rooms/items/photos, and disputes when entering a property screen. */
    suspend fun refreshPropertyData(propertyId: String): SyncResult {
        var result = SyncResult.ok()
        result = result.merge(
            SyncResult.from("property", runCatching {
                container.propertyRepository.refreshProperty(propertyId)
            }.exceptionOrNull()),
        )
        result = result.merge(container.inspectionRepository.syncForProperty(propertyId))
        result = result.merge(container.disputeRepository.syncForProperty(propertyId))
        return result
    }

    suspend fun syncForCompare(propertyId: String): SyncResult {
        var result = SyncResult.ok()
        result = result.merge(container.inspectionRepository.syncForProperty(propertyId))
        result = result.merge(container.disputeRepository.syncForProperty(propertyId))
        return result
    }

    suspend fun syncPropertyForReport(propertyId: String): SyncResult {
        var result = SyncResult.ok()
        result = result.merge(
            SyncResult.from("property", runCatching {
                container.propertyRepository.refreshProperty(propertyId)
            }.exceptionOrNull()),
        )
        result = result.merge(container.inspectionRepository.syncForProperty(propertyId))
        result = result.merge(container.disputeRepository.syncForProperty(propertyId))
        result = result.merge(
            SyncResult.from("photo uploads", runCatching {
                container.inspectionRepository.syncPendingUploads()
            }.exceptionOrNull()),
        )
        result = result.merge(
            SyncResult.from("photo download", runCatching {
                container.inspectionRepository.downloadRemotePhotos(propertyId)
            }.exceptionOrNull()),
        )
        result = result.merge(
            SyncResult.from("signatures", runCatching {
                container.inspectionRepository.hydrateSignaturesForReport(propertyId)
            }.exceptionOrNull()),
        )
        val property = container.propertyRepository.getProperty(propertyId)
        result = result.merge(
            SyncResult.from("landlord profile", runCatching {
                property?.landlordId?.let { container.authRepository.refreshProfile(it) }
            }.exceptionOrNull()),
        )
        result = result.merge(
            SyncResult.from("tenant profile", runCatching {
                property?.tenantId?.let { container.authRepository.refreshProfile(it) }
            }.exceptionOrNull()),
        )
        return result
    }

    private suspend fun registerFcmToken(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        container.authRepository.updateFcmToken(uid, token)
    }
}
