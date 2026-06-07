package com.example.tenant_landlorddisputedocumenter.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    @Test
    fun concurrent_marks_are_all_retained() {
        val cache = SyncCache()
        val threads = 8
        val perThread = 2000
        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                try {
                    for (i in 0 until perThread) cache.markPropertySynced("p-$t-$i")
                } finally {
                    done.countDown()
                }
            }
        }
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        // A non-thread-safe backing map can drop entries during concurrent resize; the
        // thread-safe cache must retain every key written.
        for (t in 0 until threads) {
            for (i in 0 until perThread) assertTrue(cache.isPropertyFresh("p-$t-$i"))
        }
    }
}
