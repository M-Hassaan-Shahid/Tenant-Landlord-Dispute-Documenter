package com.example.tenant_landlorddisputedocumenter.data.local

/** Clears all Room tables on logout so the next user does not see stale data. */
suspend fun ProofNestDatabase.clearSessionCache() {
    clearAllTables()
}
