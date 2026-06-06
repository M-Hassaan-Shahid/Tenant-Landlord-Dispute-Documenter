package com.example.tenant_landlorddisputedocumenter.data.repository

import com.example.tenant_landlorddisputedocumenter.data.local.dao.DisputeDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.PropertyDao
import com.example.tenant_landlorddisputedocumenter.data.local.entity.PropertyEntity
import com.example.tenant_landlorddisputedocumenter.data.remote.FirestorePaths
import com.example.tenant_landlorddisputedocumenter.data.remote.firestoreWrite
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.util.Ids
import com.example.tenant_landlorddisputedocumenter.util.InviteCode
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PropertyRepository(
    private val firestore: FirebaseFirestore,
    private val propertyDao: PropertyDao,
    private val notificationRepository: NotificationRepository,
    private val inspectionRepository: InspectionRepository,
    private val disputeDao: DisputeDao,
) {
    /** Stream of properties the user is involved with — either as landlord or tenant. */
    fun observeForUser(uid: String): Flow<List<Property>> =
        propertyDao.observeForUser(uid).map { list -> list.map { it.toDomain() } }

    fun observeProperty(id: String): Flow<Property?> =
        propertyDao.observe(id).map { it?.toDomain() }

    suspend fun getProperty(id: String): Property? =
        propertyDao.get(id)?.toDomain()

    /** Fetches the latest property document from Firestore into Room. */
    suspend fun refreshProperty(propertyId: String): Property? = runCatching {
        val snap = firestore.collection(FirestorePaths.PROPERTIES).document(propertyId).get().await()
        if (!snap.exists()) return@runCatching null
        val entity = PropertyEntity.from(snap.toProperty())
        propertyDao.upsert(entity)
        entity.toDomain()
    }.getOrNull()

    suspend fun createProperty(
        landlordId: String,
        address: String,
        rent: Double,
        deposit: Double,
        leaseStartMillis: Long,
        leaseEndMillis: Long,
    ): Outcome<Property> = runCatching {
        // Loop a handful of times in case of collision. Realistically this never iterates twice.
        val inviteCode = generateUniqueCode()
        val property = Property(
            id = Ids.newId(),
            landlordId = landlordId,
            tenantId = null,
            address = address,
            rent = rent,
            deposit = deposit,
            leaseStartMillis = leaseStartMillis,
            leaseEndMillis = leaseEndMillis,
            inviteCode = inviteCode,
            status = PropertyStatus.PENDING,
        )
        propertyDao.upsert(PropertyEntity.from(property))
        pushProperty(property)
        registerInviteCode(property.inviteCode, property.id, property.landlordId)
        property
    }.fold(::ok, ::fail)

    suspend fun joinPropertyByCode(tenantId: String, rawCode: String): Outcome<Property> = runCatching {
        val code = InviteCode.normalize(rawCode)
        require(InviteCode.isValid(code)) { "Invite code must be 6 characters." }
        val inviteSnap = firestore.collection(FirestorePaths.INVITE_CODES).document(code).get().await()
        if (!inviteSnap.exists()) error("No property with that code.")
        val propertyId = inviteSnap.getString("propertyId") ?: error("Invalid invite code.")
        val doc = firestore.collection(FirestorePaths.PROPERTIES).document(propertyId).get().await()
        if (!doc.exists()) error("Property no longer exists.")
        val property = doc.toProperty()
        require(property.landlordId != tenantId) { "You cannot join your own property as a tenant." }
        require(property.tenantId == null || property.tenantId == tenantId) { "Property already has a tenant." }
        val updated = property.copy(
            tenantId = tenantId,
            // Tenant requests start in PENDING_APPROVAL; landlord must approve before becoming ACTIVE.
            status = when (property.status) {
                PropertyStatus.PENDING, PropertyStatus.REJECTED -> PropertyStatus.PENDING_APPROVAL
                else -> property.status
            },
            updatedAtMillis = System.currentTimeMillis(),
        )
        propertyDao.upsert(PropertyEntity.from(updated))
        pushProperty(updated)
        notificationRepository.push(
            recipientUid = updated.landlordId,
            type = com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType.TENANT_JOINED,
            title = "Tenant Request",
            body = "A tenant has requested to join ${updated.address}.",
            propertyId = updated.id
        )
        updated
    }.fold(::ok, ::fail)

    /** Landlord approves a pending tenant request and unlocks the property. */
    suspend fun approveTenant(propertyId: String): Outcome<Unit> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        require(existing.status == PropertyStatus.PENDING_APPROVAL) {
            "No pending tenant request to approve."
        }
        val updated = existing.copy(
            status = PropertyStatus.ACTIVE,
            updatedAtMillis = System.currentTimeMillis(),
        )
        propertyDao.upsert(updated)
        val domain = updated.toDomain()
        pushProperty(domain)
        domain.tenantId?.let { tenantId ->
            notificationRepository.push(
                recipientUid = tenantId,
                type = NotificationType.TENANT_APPROVED,
                title = "Request approved",
                body = "You can now start the move-in inspection for ${domain.address}.",
                propertyId = propertyId,
            )
        }
        Unit
    }.fold(::ok, ::fail)

    /** Landlord rejects the pending tenant request. Clears the tenant link so a new join can happen. */
    suspend fun rejectTenant(propertyId: String): Outcome<Unit> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        require(existing.status == PropertyStatus.PENDING_APPROVAL) {
            "No pending tenant request to reject."
        }
        val rejectedTenantId = existing.tenantId
        val updated = existing.copy(
            tenantId = null,
            status = PropertyStatus.REJECTED,
            updatedAtMillis = System.currentTimeMillis(),
        )
        propertyDao.upsert(updated)
        val domain = updated.toDomain()
        pushProperty(domain)
        rejectedTenantId?.let { tenantId ->
            notificationRepository.push(
                recipientUid = tenantId,
                type = NotificationType.TENANT_REJECTED,
                title = "Request declined",
                body = "Your request to join ${domain.address} was declined.",
                propertyId = propertyId,
            )
        }
        Unit
    }.fold(::ok, ::fail)

    /** Records that one party finished documenting an inspection phase (before signing). */
    suspend fun markInspectionSubmitted(propertyId: String, phase: InspectionPhase): Outcome<Unit> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        val now = System.currentTimeMillis()
        val updated = when (phase) {
            InspectionPhase.MOVE_IN -> existing.copy(
                moveInInspectionSubmittedAtMillis = now,
                updatedAtMillis = now,
            )
            InspectionPhase.MOVE_OUT -> existing.copy(
                moveOutInspectionSubmittedAtMillis = now,
                updatedAtMillis = now,
            )
        }
        propertyDao.upsert(updated)
        pushProperty(updated.toDomain())
        Unit
    }.fold(::ok, ::fail)

    suspend fun updateStatus(propertyId: String, status: PropertyStatus): Outcome<Unit> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        val updated = existing.copy(status = status, updatedAtMillis = System.currentTimeMillis())
        propertyDao.upsert(updated)
        pushProperty(updated.toDomain())
        Unit
    }.fold(::ok, ::fail)

    /** Landlord unlocks the move-out inspection phase after move-in is fully signed. */
    suspend fun startMoveOut(propertyId: String, landlordId: String): Outcome<Property> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        require(existing.landlordId == landlordId) { "Only the landlord can start move-out." }
        require(existing.status == PropertyStatus.OCCUPIED) {
            "Move-out can only start after move-in is signed by both parties."
        }
        val updated = existing.copy(
            status = PropertyStatus.MOVE_OUT,
            updatedAtMillis = System.currentTimeMillis(),
        )
        propertyDao.upsert(updated)
        val domain = updated.toDomain()
        pushProperty(domain)
        domain.tenantId?.let { tenantId ->
            notificationRepository.push(
                recipientUid = tenantId,
                type = NotificationType.SIGNATURE_REQUESTED,
                title = "Move-out inspection started",
                body = "Your landlord started move-out for ${domain.address}.",
                propertyId = propertyId,
            )
        }
        domain
    }.fold(::ok, ::fail)

    /** Mirror local properties down from Firestore. */
    suspend fun syncForUser(uid: String) {
        val localBefore = propertyDao.listIdsForUser(uid)
        val byLandlord = firestore.collection(FirestorePaths.PROPERTIES)
            .whereEqualTo("landlordId", uid).get().await()
        val byTenant = firestore.collection(FirestorePaths.PROPERTIES)
            .whereEqualTo("tenantId", uid).get().await()
        val all = (byLandlord.documents + byTenant.documents)
            .distinctBy { it.id }
            .map { it.toProperty() }
        val remoteIds = all.map { it.id }.toSet()
        (localBefore - remoteIds).forEach { removedId ->
            inspectionRepository.clearLocalDataForProperty(removedId)
            disputeDao.deleteForProperty(removedId)
        }
        propertyDao.upsertAll(all.map(PropertyEntity::from))
        all.forEach { ensureInviteCodeRegistered(it) }
        if (remoteIds.isEmpty()) {
            propertyDao.deleteAllForUser(uid)
        } else {
            propertyDao.deleteForUserExcept(uid, remoteIds.toList())
        }
    }

    private suspend fun requirePropertyEntity(propertyId: String): PropertyEntity {
        propertyDao.get(propertyId)?.let { return it }
        val snap = firestore.collection(FirestorePaths.PROPERTIES).document(propertyId).get().await()
        if (!snap.exists()) error("Property not found.")
        val entity = PropertyEntity.from(snap.toProperty())
        propertyDao.upsert(entity)
        return entity
    }

    private suspend fun pushProperty(p: Property) {
        firestoreWrite("property") {
            firestore.collection(FirestorePaths.PROPERTIES).document(p.id)
                .set(p.toFirestoreMap()).await()
        }
    }

    private suspend fun ensureInviteCodeRegistered(property: Property) {
        if (property.inviteCode.isBlank()) return
        val snap = firestore.collection(FirestorePaths.INVITE_CODES)
            .document(property.inviteCode).get().await()
        if (!snap.exists()) {
            registerInviteCode(property.inviteCode, property.id, property.landlordId)
        }
    }

    private suspend fun registerInviteCode(code: String, propertyId: String, landlordId: String) {
        firestoreWrite("invite code") {
            firestore.collection(FirestorePaths.INVITE_CODES).document(code).set(
                mapOf(
                    "propertyId" to propertyId,
                    "landlordId" to landlordId,
                    "inviteCode" to code,
                ),
            ).await()
        }
    }

    private suspend fun generateUniqueCode(): String {
        repeat(12) {
            val candidate = InviteCode.generate()
            val snap = firestore.collection(FirestorePaths.INVITE_CODES).document(candidate).get().await()
            if (!snap.exists()) return candidate
        }
        return InviteCode.generate()
    }

    // ---------- Firestore mapping helpers ----------

    private fun Property.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "landlordId" to landlordId,
        "tenantId" to tenantId,
        "address" to address,
        "rent" to rent,
        "deposit" to deposit,
        "leaseStartMillis" to leaseStartMillis,
        "leaseEndMillis" to leaseEndMillis,
        "inviteCode" to inviteCode,
        "status" to status.name,
        "moveInInspectionSubmittedAtMillis" to moveInInspectionSubmittedAtMillis,
        "moveOutInspectionSubmittedAtMillis" to moveOutInspectionSubmittedAtMillis,
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toProperty(): Property = Property(
        id = getString("id") ?: id,
        landlordId = getString("landlordId").orEmpty(),
        tenantId = getString("tenantId"),
        address = getString("address").orEmpty(),
        rent = getDouble("rent") ?: 0.0,
        deposit = getDouble("deposit") ?: 0.0,
        leaseStartMillis = getLong("leaseStartMillis") ?: 0L,
        leaseEndMillis = getLong("leaseEndMillis") ?: 0L,
        inviteCode = getString("inviteCode").orEmpty(),
        status = PropertyStatus.from(getString("status")),
        moveInInspectionSubmittedAtMillis = getLong("moveInInspectionSubmittedAtMillis"),
        moveOutInspectionSubmittedAtMillis = getLong("moveOutInspectionSubmittedAtMillis"),
        createdAtMillis = getLong("createdAtMillis") ?: System.currentTimeMillis(),
        updatedAtMillis = getLong("updatedAtMillis") ?: System.currentTimeMillis(),
    )

    private fun <T> ok(value: T): Outcome<T> = Outcome.Success(value)
    private fun fail(t: Throwable): Outcome<Nothing> = Outcome.Failure(t, userMessage(t))

    private fun userMessage(t: Throwable): String {
        val raw = t.localizedMessage.orEmpty()
        return when {
            raw.contains("PERMISSION_DENIED", ignoreCase = true) ||
                raw.contains("permission", ignoreCase = true) ->
                "Permission denied. Deploy the latest Firestore rules, or ask the landlord to re-share the invite code."
            else -> raw.ifBlank { "Something went wrong. Please try again." }
        }
    }
}
