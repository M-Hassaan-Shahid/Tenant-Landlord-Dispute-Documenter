package com.example.tenant_landlorddisputedocumenter.data.repository

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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DisputeRepository(
    private val disputeDao: DisputeDao,
    private val firestore: FirebaseFirestore,
) {
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
        val dispute = Dispute(
            id = Ids.newId(),
            propertyId = propertyId,
            itemId = itemId,
            raisedByUid = raisedByUid,
            raisedByRole = raisedByRole,
            reason = reason.trim(),
            counterPhotoIds = counterPhotoIds,
            counterNote = counterNote.trim(),
        )
        disputeDao.upsert(DisputeEntity.from(dispute))
        pushDispute(dispute)
        return dispute
    }

    suspend fun resolve(disputeId: String, resolutionNote: String, status: DisputeStatus) {
        val existing = disputeDao.get(disputeId)?.toDomain() ?: return
        val updated = existing.copy(
            resolutionNote = resolutionNote.trim(),
            status = status,
            resolvedAtMillis = System.currentTimeMillis(),
        )
        disputeDao.upsert(DisputeEntity.from(updated))
        pushDispute(updated)
    }

    suspend fun syncForProperty(propertyId: String): SyncResult = runCatching {
        val remote = firestore.collection(FirestorePaths.DISPUTES)
            .whereEqualTo("propertyId", propertyId).get().await()
        val disputes = remote.documents.map { it.toDispute() }
        disputeDao.upsertAll(disputes.map(DisputeEntity::from))
    }.fold(
        onSuccess = { SyncResult.ok() },
        onFailure = { SyncResult.from("disputes", it) },
    )

    private suspend fun pushDispute(dispute: Dispute) {
        firestoreWrite("dispute") {
            firestore.collection(FirestorePaths.DISPUTES).document(dispute.id)
                .set(dispute.toFirestoreMap()).await()
        }
    }

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
