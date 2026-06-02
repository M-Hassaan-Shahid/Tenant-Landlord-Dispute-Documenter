package com.example.tenant_landlorddisputedocumenter.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncResultTest {

    @Test
    fun merge_collectsErrors() {
        val merged = SyncResult.ok()
            .merge(SyncResult.from("step-a", IllegalStateException("fail")))
            .merge(SyncResult.ok())
        assertFalse(merged.succeeded)
        assertTrue(merged.errors.single().contains("step-a"))
    }
}
