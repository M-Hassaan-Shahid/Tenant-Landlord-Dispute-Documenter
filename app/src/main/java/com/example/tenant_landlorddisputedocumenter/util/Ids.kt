package com.example.tenant_landlorddisputedocumenter.util

import java.util.UUID

/** Centralized ID generation so we can swap to Firestore-compatible IDs later if needed. */
object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
