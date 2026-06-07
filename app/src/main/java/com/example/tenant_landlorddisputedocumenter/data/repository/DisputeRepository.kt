package com.example.tenant_landlorddisputedocumenter.data.repository

import android.content.Context
import com.example.tenant_landlorddisputedocumenter.data.SyncCache
import com.example.tenant_landlorddisputedocumenter.data.SyncResult
import com.example.tenant_landlorddisputedocumenter.data.local.dao.DisputeDao
import com.example.tenant_landlorddisputedocumenter.data.local.entity.DisputeEntity
import com.example.tenant_landlorddisputedocumenter.data.remote.FirestorePaths
import com.example.tenant_landlorddisputedocumenter.data.remote.firestoreWrite
import com.example.tenant_landlorddisputedocumenter.data.remote.stringList
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import com.example.tenant_landlorddisputedocumenter.util.Ids
import com.example.tenant_landlorddisputedocumenter.util.InputValidation
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DisputeRepository(
    private val appContext: Context,
    private val disputeDao: DisputeDao,
    private val firestore: FirebaseFirestore,
    private val syncCache: SyncCache,
) {
    private companion object {
        const val PREFERENCES = "proofnest_dispute_drafts"
        const val KEY_PENDING_PREFIX = "pending_dispute_"
    }

    fun observeForProperty(propertyId: String): Flow<List<Dispute>> =
        disputeDao.observeForProperty(propertyId).map { list -> list.map { it.toDomain() } }

    fun observeForItem(itemId: String): Flow<List<Dispute>> =
        disputeDao.observeForItem(itemId).map { list -> list.map { it.toDomain() } }

    suspend fun raise(
        propertyId: String,
        itemId: String,
        raisedByUid: String,
        raisedByRole: UserRole,
        reason: String,
        counterPhotoIds: List<String> = emptyList(),
        counterNote: String = "",
    ): Dispute {
        val trimmedReason = InputValidation.trimToMax(reason)
        require(trimmedReason.isNotBlank()) { "Please provide a reason for the dispute." }
        require(disputeDao.countOpenForItem(itemId) == 0) {
            "An open dispute already exists for this item."
        }
        val trimmedCounterNote = InputValidation.trimToMax(counterNote)
        val pendingDisputeId = nextDisputeId(propertyId, itemId, raisedByUid)
        val dispute = Dispute(
            id = pendingDisputeId,
            propertyId = propertyId,
            itemId = itemId,
            raisedByUid = raisedByUid,
            raisedByRole = raisedByRole,
            reason = trimmedReason,
            counterPhotoIds = counterPhotoIds,
            counterNote = trimmedCounterNote,
        )
        disputeDao.upsert(DisputeEntity.from(dispute))
        try {
            pushDisputeLock(dispute)
            pushDispute(dispute)
            clearPendingDisputeId(propertyId, itemId, raisedByUid)
        } catch (e: Exception) {
            disputeDao.delete(dispute.id)
            throw e
        }
        return dispute
    }

    suspend fun resolve(
        disputeId: String,
        resolverUid: String,
        resolutionNote: String,
        status: DisputeStatus,
    ) {
        require(status == DisputeStatus.RESOLVED || status == DisputeStatus.UNRESOLVED) {
            "Invalid dispute resolution status."
        }
        val existing = disputeDao.get(disputeId)?.toDomain() ?: error("Dispute not found.")
        require(existing.status == DisputeStatus.OPEN) { "This dispute is already closed." }
        require(existing.raisedByUid != resolverUid) {
            "You cannot resolve a dispute you raised."
        }
        val trimmedNote = InputValidation.trimToMax(resolutionNote)
        if (status == DisputeStatus.RESOLVED && trimmedNote.isBlank()) {
            error("Please provide a resolution note when marking a dispute resolved.")
        }
        val updated = existing.copy(
            resolutionNote = trimmedNote,
            status = status,
            resolvedAtMillis = System.currentTimeMillis(),
        )
        val previous = existing
        disputeDao.upsert(DisputeEntity.from(updated))
        try {
            pushDisputeAndReleaseLock(updated)
            clearPendingDisputeId(updated.propertyId, updated.itemId, updated.raisedByUid)
        } catch (e: Exception) {
            disputeDao.upsert(DisputeEntity.from(previous))
            throw e
        }
    }

    suspend fun syncForProperty(propertyId: String): SyncResult = runCatching {
        val remote = firestore.collection(FirestorePaths.DISPUTES)
            .whereEqualTo("propertyId", propertyId).get().await()
        val disputes = remote.documents.map { it.toDispute() }
        disputes.forEach { dispute ->
            if (loadPendingDisputeId(dispute.propertyId, dispute.itemId, dispute.raisedByUid) == dispute.id) {
                clearPendingDisputeId(dispute.propertyId, dispute.itemId, dispute.raisedByUid)
            }
        }
        disputeDao.upsertAll(disputes.map(DisputeEntity::from))
    }.fold(
        onSuccess = { SyncResult.ok() },
        onFailure = { SyncResult.from("disputes", it) },
    )

    private suspend fun pushDisputeLock(dispute: Dispute) {
        syncCache.invalidateProperty(dispute.propertyId)
        firestoreWrite("dispute lock") {
            firestore.collection(FirestorePaths.DISPUTE_ITEM_LOCKS)
                .document(disputeLockId(dispute.propertyId, dispute.itemId))
                .set(
                    mapOf(
                        "propertyId" to dispute.propertyId,
                        "itemId" to dispute.itemId,
                        "disputeId" to dispute.id,
                        "raisedByUid" to dispute.raisedByUid,
                    ),
                ).await()
        }
    }

    private suspend fun pushDispute(dispute: Dispute) {
        firestoreWrite("dispute") {
            firestore.collection(FirestorePaths.DISPUTES).document(dispute.id)
                .set(dispute.toFirestoreMap()).await()
        }
    }

    private fun loadPendingDisputeId(propertyId: String, itemId: String, raisedByUid: String): String? =
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(pendingKey(propertyId, itemId, raisedByUid), null)

    private fun savePendingDisputeId(propertyId: String, itemId: String, raisedByUid: String, disputeId: String) {
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(pendingKey(propertyId, itemId, raisedByUid), disputeId)
            .apply()
    }

    private fun clearPendingDisputeId(propertyId: String, itemId: String, raisedByUid: String) {
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(pendingKey(propertyId, itemId, raisedByUid))
            .apply()
    }

    private fun pendingKey(propertyId: String, itemId: String, raisedByUid: String): String =
        KEY_PENDING_PREFIX + propertyId + "_" + itemId + "_" + raisedByUid

    private suspend fun nextDisputeId(propertyId: String, itemId: String, raisedByUid: String): String {
        val pending = loadPendingDisputeId(propertyId, itemId, raisedByUid)
        if (pending != null) {
            val exists = runCatching {
                firestore.collection(FirestorePaths.DISPUTES).document(pending).get().await().exists()
            }.getOrDefault(false)
            if (!exists) return pending
            clearPendingDisputeId(propertyId, itemId, raisedByUid)
        }
        return Ids.newId().also { savePendingDisputeId(propertyId, itemId, raisedByUid, it) }
    }

    private suspend fun pushDisputeAndReleaseLock(dispute: Dispute) {
        syncCache.invalidateProperty(dispute.propertyId)
        firestoreWrite("dispute") {
            val batch = firestore.batch()
            // Update only the keys the security rule permits on resolve; a full
            // set() would touch immutable fields and be rejected by the rule.
            batch.update(
                firestore.collection(FirestorePaths.DISPUTES).document(dispute.id),
                "status", dispute.status.name,
                "resolutionNote", dispute.resolutionNote,
                "resolvedAtMillis", dispute.resolvedAtMillis,
            )
            batch.delete(
                firestore.collection(FirestorePaths.DISPUTE_ITEM_LOCKS)
                    .document(disputeLockId(dispute.propertyId, dispute.itemId)),
            )
            batch.commit().await()
        }
    }

    private fun disputeLockId(propertyId: String, itemId: String): String = "${propertyId}_${itemId}"

    private fun Dispute.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "propertyId" to propertyId,
        "itemId" to itemId,
        "raisedByUid" to raisedByUid,
        "raisedByRole" to raisedByRole.name,
        "reason" to reason,
        "counterPhotoIds" to counterPhotoIds,
        "counterNote" to counterNote,
        "resolutionNote" to resolutionNote,
        "status" to status.name,
        "raisedAtMillis" to raisedAtMillis,
        "resolvedAtMillis" to resolvedAtMillis,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toDispute(): Dispute = Dispute(
        id = getString("id") ?: id,
        propertyId = getString("propertyId").orEmpty(),
        itemId = getString("itemId").orEmpty(),
        raisedByUid = getString("raisedByUid").orEmpty(),
        raisedByRole = UserRole.from(getString("raisedByRole")),
        reason = getString("reason").orEmpty(),
        counterPhotoIds = stringList("counterPhotoIds"),
        counterNote = getString("counterNote").orEmpty(),
        resolutionNote = getString("resolutionNote") ?: "",
        status = DisputeStatus.from(getString("status")),
        raisedAtMillis = getLong("raisedAtMillis") ?: System.currentTimeMillis(),
        resolvedAtMillis = getLong("resolvedAtMillis"),
    )
}
