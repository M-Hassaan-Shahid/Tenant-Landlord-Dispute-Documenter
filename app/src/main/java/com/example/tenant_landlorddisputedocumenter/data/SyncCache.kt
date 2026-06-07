package com.example.tenant_landlorddisputedocumenter.data

/**
 * In-memory TTL cache so we show Room data immediately and skip redundant Firestore pulls.
 * Invalidated on explicit pull-to-refresh (force sync) or after local writes.
 */
class SyncCache {
    private val propertySyncedAt = mutableMapOf<String, Long>()
    private val userSyncedAt = mutableMapOf<String, Long>()

    fun isPropertyFresh(propertyId: String, ttlMs: Long = PROPERTY_TTL_MS): Boolean {
        val last = propertySyncedAt[propertyId] ?: return false
        return System.currentTimeMillis() - last < ttlMs
    }

    fun markPropertySynced(propertyId: String) {
        propertySyncedAt[propertyId] = System.currentTimeMillis()
    }

    fun invalidateProperty(propertyId: String) {
        propertySyncedAt.remove(propertyId)
    }

    fun isUserFresh(uid: String, ttlMs: Long = USER_TTL_MS): Boolean {
        val last = userSyncedAt[uid] ?: return false
        return System.currentTimeMillis() - last < ttlMs
    }

    fun markUserSynced(uid: String) {
        userSyncedAt[uid] = System.currentTimeMillis()
    }

    fun invalidateUser(uid: String) {
        userSyncedAt.remove(uid)
    }

    fun clearAll() {
        propertySyncedAt.clear()
        userSyncedAt.clear()
    }

    companion object {
        const val PROPERTY_TTL_MS = 3 * 60 * 1000L
        const val USER_TTL_MS = 5 * 60 * 1000L
    }
}
