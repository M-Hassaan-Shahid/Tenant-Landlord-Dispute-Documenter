package com.example.tenant_landlorddisputedocumenter.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Clears all Room tables on logout so the next user does not see stale data. */
suspend fun ProofNestDatabase.clearSessionCache() {
    withContext(Dispatchers.IO) {
        clearAllTables()
    }
}
