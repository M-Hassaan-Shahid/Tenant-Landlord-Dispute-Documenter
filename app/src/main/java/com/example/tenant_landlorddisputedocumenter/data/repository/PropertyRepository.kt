package com.example.tenant_landlorddisputedocumenter.data.repository

import com.example.tenant_landlorddisputedocumenter.data.SyncCache
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
import com.example.tenant_landlorddisputedocumenter.util.InputValidation
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
    private val syncCache: SyncCache,
) {
    private fun invalidatePropertyCache(propertyId: String) {
        syncCache.invalidateProperty(propertyId)
    }

    fun observeForUser(uid: String): Flow<List<Property>> =
        propertyDao.observeForUser(uid).map { list -> list.map { it.toDomain() } }

    fun observeProperty(id: String): Flow<Property?> =
        propertyDao.observe(id).map { it?.toDomain() }

    suspend fun getProperty(id: String): Property? =
        propertyDao.get(id)?.toDomain()

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
        InputValidation.validateAddress(address)?.let { error(it) }
        require(rent >= InputValidation.MIN_RENT) { "Rent must be at least ${InputValidation.MIN_RENT.toInt()}." }
        require(deposit >= InputValidation.MIN_RENT) { "Deposit must be at least ${InputValidation.MIN_RENT.toInt()}." }
        require(leaseEndMillis > leaseStartMillis) { "Lease end must be after lease start." }

        val inviteCode = generateUniqueCode()
        val property = Property(
            id = Ids.newId(),
            landlordId = landlordId,
            tenantId = null,
            address = address.trim(),
            rent = rent,
            deposit = deposit,
            leaseStartMillis = leaseStartMillis,
            leaseEndMillis = leaseEndMillis,
            inviteCode = inviteCode,
            status = PropertyStatus.PENDING,
        )
        propertyDao.upsert(PropertyEntity.from(property))
        try {
            pushProperty(property)
            registerInviteCode(property.inviteCode, property.id, property.landlordId)
        } catch (e: Exception) {
            propertyDao.delete(property.id)
            throw e
        }
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
        require(property.tenantId == null) { "Property already has a tenant." }
        require(property.status == PropertyStatus.PENDING || property.status == PropertyStatus.REJECTED) {
            "This property is not accepting new tenant requests."
        }
        val updated = property.copy(
            tenantId = tenantId,
            status = PropertyStatus.PENDING_APPROVAL,
            updatedAtMillis = System.currentTimeMillis(),
        )
        val previous = PropertyEntity.from(property)
        propertyDao.upsert(PropertyEntity.from(updated))
        try {
            pushProperty(updated)
            notificationRepository.push(
                recipientUid = updated.landlordId,
                type = NotificationType.TENANT_JOINED,
                title = "Tenant Request",
                body = "A tenant has requested to join ${updated.address}.",
                propertyId = updated.id,
            )
        } catch (e: Exception) {
            propertyDao.upsert(previous)
            throw e
        }
        updated
    }.fold(::ok, ::fail)

    suspend fun approveTenant(propertyId: String, landlordId: String): Outcome<Unit> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        require(existing.landlordId == landlordId) { "Only the landlord can approve tenant requests." }
        require(existing.status == PropertyStatus.PENDING_APPROVAL) {
            "No pending tenant request to approve."
        }
        require(existing.tenantId != null) { "No tenant is linked to this property." }
        val updated = existing.copy(
            status = PropertyStatus.ACTIVE,
            updatedAtMillis = System.currentTimeMillis(),
        )
        propertyDao.upsert(updated)
        val domain = updated.toDomain()
        try {
            pushProperty(domain)
            domain.tenantId?.let { tenantId ->
                notificationRepository.push(
                    recipientUid = tenantId,
                    type = NotificationType.TENANT_APPROVED,
                    title = "Request approved",
                    body = "You're approved for ${domain.address}. Wait for the landlord to document move-in, then review and sign.",
                    propertyId = propertyId,
                )
            }
        } catch (e: Exception) {
            propertyDao.upsert(existing)
            throw e
        }
        Unit
    }.fold(::ok, ::fail)

    suspend fun rejectTenant(propertyId: String, landlordId: String): Outcome<Unit> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        require(existing.landlordId == landlordId) { "Only the landlord can reject tenant requests." }
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
        try {
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
        } catch (e: Exception) {
            propertyDao.upsert(existing)
            throw e
        }
        Unit
    }.fold(::ok, ::fail)

    suspend fun markInspectionSubmitted(
        propertyId: String,
        phase: InspectionPhase,
        submittedByUid: String,
    ): Outcome<Unit> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        require(existing.landlordId == submittedByUid) {
            when (phase) {
                InspectionPhase.MOVE_IN -> "Only the landlord can submit the move-in inspection."
                InspectionPhase.MOVE_OUT -> "Only the landlord can submit the move-out inspection."
            }
        }
        when (phase) {
            InspectionPhase.MOVE_IN -> {
                require(existing.status == PropertyStatus.ACTIVE) {
                    "Move-in inspection can only be submitted while the property is active."
                }
                require(existing.moveInInspectionSubmittedAtMillis == null) {
                    "Move-in inspection was already submitted."
                }
            }
            InspectionPhase.MOVE_OUT -> {
                require(existing.status == PropertyStatus.MOVE_OUT) {
                    "Move-out inspection can only be submitted during move-out."
                }
                require(existing.moveOutInspectionSubmittedAtMillis == null) {
                    "Move-out inspection was already submitted."
                }
            }
        }
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
        try {
            pushProperty(updated.toDomain())
        } catch (e: Exception) {
            propertyDao.upsert(existing)
            throw e
        }
        Unit
    }.fold(::ok, ::fail)

    suspend fun updateStatus(
        propertyId: String,
        status: PropertyStatus,
        callerUid: String,
    ): Outcome<Unit> = runCatching {
        val existing = requirePropertyEntity(propertyId)
        require(existing.landlordId == callerUid || existing.tenantId == callerUid) {
            "You are not a member of this property."
        }
        when (status) {
            PropertyStatus.OCCUPIED -> {
                require(existing.status == PropertyStatus.ACTIVE) {
                    "Move-in must be active before marking occupied."
                }
                require(existing.moveInInspectionSubmittedAtMillis != null) {
                    "Move-in inspection must be submitted first."
                }
                require(existing.tenantId != null) { "Property has no tenant." }
                require(
                    inspectionRepository.isPhaseSignedByBoth(
                        propertyId,
                        InspectionPhase.MOVE_IN,
                        existing.landlordId,
                        existing.tenantId,
                    ),
                ) { "Both parties must sign move-in before the property is occupied." }
            }
            PropertyStatus.CLOSED -> {
                require(existing.status == PropertyStatus.MOVE_OUT) {
                    "Move-out must be in progress before closing the property."
                }
                require(existing.moveOutInspectionSubmittedAtMillis != null) {
                    "Move-out inspection must be submitted first."
                }
                require(existing.tenantId != null) { "Property has no tenant." }
                require(
                    inspectionRepository.isPhaseSignedByBoth(
                        propertyId,
                        InspectionPhase.MOVE_OUT,
                        existing.landlordId,
                        existing.tenantId,
                    ),
                ) { "Both parties must sign move-out before closing the property." }
            }
            else -> error("Unsupported status transition to ${status.name}.")
        }
        val updated = existing.copy(status = status, updatedAtMillis = System.currentTimeMillis())
        propertyDao.upsert(updated)
        try {
            pushProperty(updated.toDomain())
        } catch (e: Exception) {
            propertyDao.upsert(existing)
            throw e
        }
        Unit
    }.fold(::ok, ::fail)

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
        try {
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
        } catch (e: Exception) {
            propertyDao.upsert(existing)
            throw e
        }
        domain
    }.fold(::ok, ::fail)

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
        invalidatePropertyCache(p.id)
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
