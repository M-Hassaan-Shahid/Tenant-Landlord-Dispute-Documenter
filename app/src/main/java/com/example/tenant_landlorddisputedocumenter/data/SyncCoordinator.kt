package com.example.tenant_landlorddisputedocumenter.data

import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** Pulls remote Firebase data into Room and schedules local lease reminders. */
class SyncCoordinator(
    private val container: ServiceContainer,
    private val cache: SyncCache = container.syncCache,
) {

    /** Fast path for dashboard: property list + alerts, no per-property deep sync. */
    suspend fun syncDashboardForUser(uid: String, force: Boolean = false): SyncResult {
        if (!force && cache.isUserFresh(uid)) {
            return SyncResult.ok()
        }
        var result = syncUserBasics(uid)
        cache.markUserSynced(uid)
        return result
    }

    suspend fun syncAllForUser(uid: String, force: Boolean = false): SyncResult {
        if (!force && cache.isUserFresh(uid)) {
            return SyncResult.ok()
        }
        var result = syncUserBasics(uid)
        val properties = container.propertyRepository.observeForUser(uid).first()
        for (property in properties) {
            result = result.merge(syncPropertyData(property.id, force = true))
        }
        result = result.merge(
            SyncResult.from("lease reminders", runCatching {
                container.notificationRepository.ensureLeaseEndingReminders(uid, properties)
            }.exceptionOrNull()),
        )
        cache.markUserSynced(uid)
        return result
    }

    /** Pulls latest property metadata and inspection/dispute rows when entering a property screen. */
    suspend fun refreshPropertyData(propertyId: String, force: Boolean = false): SyncResult {
        return syncPropertyData(propertyId, force)
    }

    suspend fun syncForCompare(propertyId: String, force: Boolean = false): SyncResult {
        if (!force && cache.isPropertyFresh(propertyId)) {
            return SyncResult.ok()
        }
        var result = SyncResult.ok()
        result = result.merge(container.inspectionRepository.syncForProperty(propertyId))
        result = result.merge(container.disputeRepository.syncForProperty(propertyId))
        if (result.succeeded) {
            cache.markPropertySynced(propertyId)
        }
        return result
    }

    suspend fun syncPropertyForReport(propertyId: String): SyncResult {
        cache.invalidateProperty(propertyId)
        var result = syncPropertyData(propertyId, force = true)
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

    fun invalidateProperty(propertyId: String) {
        cache.invalidateProperty(propertyId)
    }

    private suspend fun syncUserBasics(uid: String): SyncResult {
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
        result = result.merge(
            SyncResult.from("photo uploads", runCatching {
                container.inspectionRepository.syncPendingUploads()
            }.exceptionOrNull()),
        )
        return result
    }

    private suspend fun syncPropertyData(propertyId: String, force: Boolean): SyncResult {
        if (!force && cache.isPropertyFresh(propertyId)) {
            return SyncResult.ok()
        }
        var result = SyncResult.ok()
        result = result.merge(
            SyncResult.from("property", runCatching {
                container.propertyRepository.refreshProperty(propertyId)
            }.exceptionOrNull()),
        )
        result = result.merge(container.inspectionRepository.syncForProperty(propertyId))
        result = result.merge(container.disputeRepository.syncForProperty(propertyId))
        if (result.succeeded) {
            cache.markPropertySynced(propertyId)
        }
        return result
    }

    private suspend fun registerFcmToken(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        container.authRepository.updateFcmToken(uid, token)
    }
}
