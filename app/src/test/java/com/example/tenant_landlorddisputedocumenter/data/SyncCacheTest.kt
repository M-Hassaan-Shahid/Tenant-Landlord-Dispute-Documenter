package com.example.tenant_landlorddisputedocumenter.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCacheTest {

    @Test
    fun property_not_fresh_until_marked() {
        val cache = SyncCache()
        assertFalse(cache.isPropertyFresh("prop-1"))
        cache.markPropertySynced("prop-1")
        assertTrue(cache.isPropertyFresh("prop-1"))
    }

    @Test
    fun invalidate_property_forces_resync() {
        val cache = SyncCache()
        cache.markPropertySynced("prop-1")
        cache.invalidateProperty("prop-1")
        assertFalse(cache.isPropertyFresh("prop-1"))
    }

    @Test
    fun clearAll_wipes_user_and_property_ttl() {
        val cache = SyncCache()
        cache.markPropertySynced("prop-1")
        cache.markUserSynced("user-1")
        cache.clearAll()
        assertFalse(cache.isPropertyFresh("prop-1"))
        assertFalse(cache.isUserFresh("user-1"))
    }
}
