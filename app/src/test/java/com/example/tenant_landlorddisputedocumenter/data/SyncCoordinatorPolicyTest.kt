package com.example.tenant_landlorddisputedocumenter.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Documents sync-cache marking rules exercised by [SyncCoordinator]. */
class SyncCoordinatorPolicyTest {

    @Test
    fun markUserSynced_only_when_all_steps_succeeded() {
        val failed = SyncResult.from("properties", IllegalStateException("network"))
        val merged = SyncResult.ok().merge(failed)
        assertFalse(merged.succeeded)

        val recovered = SyncResult.ok()
        assertTrue(recovered.succeeded)
    }
}
