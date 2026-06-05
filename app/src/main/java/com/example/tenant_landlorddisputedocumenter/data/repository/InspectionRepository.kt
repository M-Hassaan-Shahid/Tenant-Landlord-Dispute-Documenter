package com.example.tenant_landlorddisputedocumenter.data.repository

import android.content.Context
import android.util.Base64
import com.example.tenant_landlorddisputedocumenter.data.SyncResult
import com.example.tenant_landlorddisputedocumenter.data.local.dao.ItemDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.PhotoDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.RoomDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.SignatureDao
import com.example.tenant_landlorddisputedocumenter.data.local.entity.ItemEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.PhotoEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.RoomEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.SignatureEntity
import com.example.tenant_landlorddisputedocumenter.data.remote.FirestorePaths
import com.example.tenant_landlorddisputedocumenter.data.remote.firestoreWrite
import com.example.tenant_landlorddisputedocumenter.data.remote.stringList
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionRoom
import com.example.tenant_landlorddisputedocumenter.domain.model.Photo
import com.example.tenant_landlorddisputedocumenter.domain.model.Signature
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import com.example.tenant_landlorddisputedocumenter.util.Ids
import com.example.tenant_landlorddisputedocumenter.data.remote.CloudinaryUploader
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.net.URL

class InspectionRepository(
    private val context: Context,
    private val roomDao: RoomDao,
    private val itemDao: ItemDao,
    private val photoDao: PhotoDao,
    private val signatureDao: SignatureDao,
    private val cloudinary: CloudinaryUploader,
    private val notificationRepository: NotificationRepository,
    private val firestore: FirebaseFirestore,
) {
    // ---------------- Rooms ----------------

    fun observeRooms(propertyId: String): Flow<List<InspectionRoom>> =
        roomDao.observeForProperty(propertyId).map { list -> list.map { it.toDomain() } }

    suspend fun addRoom(propertyId: String, name: String): InspectionRoom {
        val existing = roomDao.listForProperty(propertyId)
        val room = InspectionRoom(
            id = Ids.newId(),
            propertyId = propertyId,
            name = name.trim(),
            sortOrder = existing.size,
        )
        roomDao.upsert(RoomEntity.from(room))
        pushRoom(room)
        return room
    }

    suspend fun deleteRoom(roomId: String) {
        val items = itemDao.listForRoom(roomId)
        val itemIds = items.map { it.id }
        if (itemIds.isNotEmpty()) {
            photoDao.deleteForItems(itemIds)
            itemDao.deleteForRoom(roomId)
        }
        roomDao.delete(roomId)
        firestoreWrite("delete room") {
            val deletes = buildList {
                for (item in items) {
                    add(firestore.collection(FirestorePaths.ITEMS).document(item.id))
                    val photoIds = item.moveInPhotoIds + item.moveOutPhotoIds
                    for (photoId in photoIds) {
                        add(firestore.collection(FirestorePaths.PHOTOS).document(photoId))
                    }
                }
                add(firestore.collection(FirestorePaths.ROOMS).document(roomId))
            }
            commitBatchedDeletes(deletes)
        }
    }

    /** True when room/item structure must not change (post move-in sign or inspection underway). */
    suspend fun isStructureLocked(propertyId: String, status: PropertyStatus): Boolean {
        if (status.inspectionStarted) return true
        val items = itemDao.listForProperty(propertyId).map { it.toDomain() }
        return when (status) {
            PropertyStatus.ACTIVE -> items.any { it.hasPhaseProgress(InspectionPhase.MOVE_IN) }
            PropertyStatus.MOVE_OUT -> items.any { it.hasPhaseProgress(InspectionPhase.MOVE_OUT) }
            else -> false
        }
    }

    suspend fun listRooms(propertyId: String): List<InspectionRoom> =
        roomDao.listForProperty(propertyId).map { it.toDomain() }

    // ---------------- Items ----------------

    fun observeItems(roomId: String): Flow<List<ChecklistItem>> =
        itemDao.observeForRoom(roomId).map { list -> list.map { it.toDomain() } }

    fun observeAllItems(propertyId: String): Flow<List<ChecklistItem>> =
        itemDao.observeForProperty(propertyId).map { list -> list.map { it.toDomain() } }

    suspend fun addItem(propertyId: String, roomId: String, name: String): ChecklistItem {
        val item = ChecklistItem(
            id = Ids.newId(),
            roomId = roomId,
            propertyId = propertyId,
            name = name.trim(),
        )
        itemDao.upsert(ItemEntity.from(item))
        pushItem(item)
        return item
    }

    suspend fun updateItem(item: ChecklistItem) {
        itemDao.upsert(ItemEntity.from(item))
        pushItem(item)
    }

    suspend fun setNote(itemId: String, phase: InspectionPhase, note: String) {
        val existing = itemDao.get(itemId)?.toDomain() ?: return
        val updated = when (phase) {
            InspectionPhase.MOVE_IN -> existing.copy(
                moveInNote = note,
                moveInTimestamp = System.currentTimeMillis(),
            )
            InspectionPhase.MOVE_OUT -> existing.copy(
                moveOutNote = note,
                moveOutTimestamp = System.currentTimeMillis(),
            )
        }
        itemDao.upsert(ItemEntity.from(updated))
        pushItem(updated)
    }

    suspend fun setRating(itemId: String, phase: InspectionPhase, rating: ConditionRating) {
        val existing = itemDao.get(itemId)?.toDomain() ?: return
        val updated = when (phase) {
            InspectionPhase.MOVE_IN -> existing.copy(
                moveInRating = rating,
                moveInTimestamp = System.currentTimeMillis(),
            )
            InspectionPhase.MOVE_OUT -> existing.copy(
                moveOutRating = rating,
                moveOutTimestamp = System.currentTimeMillis(),
            )
        }
        itemDao.upsert(ItemEntity.from(updated))
        pushItem(updated)
    }

    // ---------------- Photos ----------------

    fun observePhotos(itemId: String, phase: InspectionPhase): Flow<List<Photo>> =
        photoDao.observeForItem(itemId, phase).map { list -> list.map { it.toDomain() } }

    suspend fun savePhoto(photo: Photo) {
        photoDao.upsert(PhotoEntity.from(photo))
        val item = itemDao.get(photo.itemId)?.toDomain() ?: return
        val withPhotoId = when (photo.phase) {
            InspectionPhase.MOVE_IN -> item.copy(
                moveInPhotoIds = (item.moveInPhotoIds + photo.id).distinct(),
                moveInTimestamp = item.moveInTimestamp ?: photo.capturedAtMillis,
            )
            InspectionPhase.MOVE_OUT -> item.copy(
                moveOutPhotoIds = (item.moveOutPhotoIds + photo.id).distinct(),
                moveOutTimestamp = item.moveOutTimestamp ?: photo.capturedAtMillis,
            )
        }
        itemDao.upsert(ItemEntity.from(withPhotoId))
        pushItem(withPhotoId)
    }

    suspend fun getPhotos(ids: List<String>): List<Photo> =
        photoDao.getMany(ids).map { it.toDomain() }

    suspend fun syncPendingUploads(): Int {
        val pending = photoDao.pendingUploads()
        var uploaded = 0
        for (entity in pending) {
            val localUri = entity.localUri ?: continue
            val file = File(localUri.removePrefix("file://"))
            if (!file.exists()) continue
            runCatching {
                val url = cloudinary.upload(
                    file,
                    folder = "photos/${entity.propertyId}/${entity.itemId}",
                )
                val updated = entity.copy(remoteUrl = url, uploaded = true)
                photoDao.upsert(updated)

                firestoreWrite("photo") {
                    firestore.collection(FirestorePaths.PHOTOS).document(updated.id)
                        .set(updated.toDomain().toFirestoreMap()).await()
                }

                uploaded++
            }
        }
        return uploaded
    }

    // ---------------- Signatures ----------------

    fun observeSignatures(propertyId: String, phase: InspectionPhase): Flow<List<Signature>> =
        signatureDao.observeForPhase(propertyId, phase).map { list -> list.map { it.toDomain() } }

    fun observeAllSignatures(propertyId: String): Flow<List<Signature>> =
        signatureDao.observeForProperty(propertyId).map { list -> list.map { it.toDomain() } }

    suspend fun saveSignature(
        propertyId: String,
        signerUid: String,
        signerRole: UserRole,
        phase: InspectionPhase,
        pngBase64: String,
        notifyRecipientUid: String?,
    ): Signature {
        val signature = Signature(
            id = Ids.newId(),
            propertyId = propertyId,
            signerUid = signerUid,
            signerRole = signerRole,
            phase = phase,
            pngBase64 = pngBase64,
            signedAtMillis = System.currentTimeMillis(),
        )
        val remoteUrl = uploadSignatureImage(signature)
        val withUrl = signature.copy(remoteUrl = remoteUrl)
        signatureDao.upsert(SignatureEntity.from(withUrl))
        pushSignature(withUrl)

        val phaseLabel = if (phase == InspectionPhase.MOVE_IN) "move-in" else "move-out"
        notifyRecipientUid?.let { recipient ->
            notificationRepository.push(
                recipientUid = recipient,
                type = com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType.SIGNATURE_REQUESTED,
                title = "${signerRole.name.lowercase().replaceFirstChar { it.uppercase() }} signed $phaseLabel",
                body = "Please review and sign the $phaseLabel record when ready.",
                propertyId = propertyId,
            )
        }

        return withUrl
    }

    suspend fun isPhaseSignedByBoth(
        propertyId: String,
        phase: InspectionPhase,
        landlordUid: String,
        tenantUid: String?,
    ): Boolean {
        // Count local signatures only — syncForProperty must not run here; its prune step can
        // delete signatures not yet visible on Firestore (race right after saveSignature).
        val landlord = signatureDao.countFor(propertyId, phase, landlordUid) > 0
        val tenant = tenantUid != null && signatureDao.countFor(propertyId, phase, tenantUid) > 0
        return landlord && tenant
    }

    // ---------------- Firestore Sync ----------------
    
    /** Removes all inspection-related Room rows for a property (e.g. after remote unlink). */
    suspend fun clearLocalDataForProperty(propertyId: String) {
        photoDao.deleteForProperty(propertyId)
        itemDao.deleteForProperty(propertyId)
        roomDao.deleteForProperty(propertyId)
        signatureDao.deleteForProperty(propertyId)
    }

    /** Downloads signature PNGs from Storage so PDF generation can render them offline. */
    suspend fun hydrateSignaturesForReport(propertyId: String) {
        for (entity in signatureDao.listForProperty(propertyId)) {
            if (entity.pngBase64.isNotBlank()) continue
            val url = entity.remoteUrl ?: continue
            runCatching {
                val bytes = URL(url).openStream().use { it.readBytes() }
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                signatureDao.upsert(entity.copy(pngBase64 = encoded))
            }
        }
    }

    suspend fun syncForProperty(propertyId: String): SyncResult = runCatching {
            // Sync Rooms
            val roomsSnap = firestore.collection(FirestorePaths.ROOMS)
                .whereEqualTo("propertyId", propertyId).get().await()
            val rooms = roomsSnap.documents.map { it.toRoom() }
            roomDao.upsertAll(rooms.map(RoomEntity::from))

            // Sync Items
            val itemsSnap = firestore.collection(FirestorePaths.ITEMS)
                .whereEqualTo("propertyId", propertyId).get().await()
            val items = itemsSnap.documents.map { it.toItem() }
            itemDao.upsertAll(items.map(ItemEntity::from))
            
            // Sync Signatures
            val sigsSnap = firestore.collection(FirestorePaths.SIGNATURES)
                .whereEqualTo("propertyId", propertyId).get().await()
            val sigs = sigsSnap.documents.map { it.toSignature() }
            val sigEntities = sigs.map { sig ->
                val existing = signatureDao.listForProperty(propertyId).find { it.id == sig.id }
                val png = when {
                    existing != null && existing.pngBase64.isNotBlank() -> existing.pngBase64
                    sig.pngBase64.isNotBlank() -> sig.pngBase64
                    else -> ""
                }
                SignatureEntity.from(sig.copy(pngBase64 = png))
            }
            signatureDao.upsertAll(sigEntities)
            
            // Sync Photos Metadata
            val photosSnap = firestore.collection(FirestorePaths.PHOTOS)
                .whereEqualTo("propertyId", propertyId).get().await()
            val photos = photosSnap.documents.map { it.toPhoto() }
            val remotePhotoIds = photos.map { it.id }.toSet()
            // Only drop uploaded orphans — pending captures exist locally before Firestore has a doc.
            photoDao.listForProperty(propertyId)
                .filter { it.uploaded && it.id !in remotePhotoIds }
                .forEach { photoDao.delete(it.id) }
            
            // Merge existing localUris to not overwrite with null when syncing from cloud
            for (p in photos) {
                val existing = photoDao.get(p.id)
                val toSave = if (existing != null) p.copy(localUri = existing.localUri) else p
                photoDao.upsert(PhotoEntity.from(toSave))
            }
            downloadRemotePhotos(propertyId)
    }.fold(
        onSuccess = { SyncResult.ok() },
        onFailure = { SyncResult.from("inspection data", it) },
    )

    private suspend fun uploadSignatureImage(signature: Signature): String {
        val bytes = Base64.decode(signature.pngBase64, Base64.DEFAULT)
        val file = File(context.cacheDir, "sig_upload_${signature.id}.png")
        file.writeBytes(bytes)
        val url = cloudinary.upload(file, folder = "signatures/${signature.propertyId}")
        file.delete()
        return url
    }

    private suspend fun commitBatchedDeletes(refs: List<DocumentReference>) {
        refs.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }

    /** Pull Storage files so PDF/report can render photos captured on another device. */
    suspend fun downloadRemotePhotos(propertyId: String): Int {
        var count = 0
        for (entity in photoDao.listForProperty(propertyId)) {
            val remoteUrl = entity.remoteUrl ?: continue
            val file = File(context.filesDir, "photos/${entity.propertyId}/${entity.id}.jpg")
            val localPath = entity.localUri?.removePrefix("file://")
            if (localPath != null && File(localPath).exists()) continue
            runCatching {
                file.parentFile?.mkdirs()
                URL(remoteUrl).openStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                photoDao.upsert(entity.copy(localUri = "file://${file.absolutePath}"))
                count++
            }
        }
        return count
    }

    private suspend fun pushRoom(room: InspectionRoom) {
        firestoreWrite("room") {
            firestore.collection(FirestorePaths.ROOMS).document(room.id)
                .set(room.toFirestoreMap()).await()
        }
    }

    private suspend fun pushItem(item: ChecklistItem) {
        firestoreWrite("checklist item") {
            firestore.collection(FirestorePaths.ITEMS).document(item.id)
                .set(item.toFirestoreMap()).await()
        }
    }

    private suspend fun pushSignature(signature: Signature) {
        firestoreWrite("signature") {
            firestore.collection(FirestorePaths.SIGNATURES).document(signature.id)
                .set(signature.toFirestoreMap()).await()
        }
    }
    
    // ---------- Mappers ----------

    private fun InspectionRoom.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id, "propertyId" to propertyId, "name" to name, "sortOrder" to sortOrder
    )

    private fun ChecklistItem.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id, "roomId" to roomId, "propertyId" to propertyId, "name" to name,
        "moveInRating" to moveInRating?.name, "moveInNote" to moveInNote, "moveInPhotoIds" to moveInPhotoIds, "moveInTimestamp" to moveInTimestamp,
        "moveOutRating" to moveOutRating?.name, "moveOutNote" to moveOutNote, "moveOutPhotoIds" to moveOutPhotoIds, "moveOutTimestamp" to moveOutTimestamp
    )
    
    private fun Signature.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "propertyId" to propertyId,
        "signerUid" to signerUid,
        "signerRole" to signerRole.name,
        "phase" to phase.name,
        "remoteUrl" to remoteUrl,
        "signedAtMillis" to signedAtMillis,
    )

    private fun Photo.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id, "itemId" to itemId, "propertyId" to propertyId, "phase" to phase.name,
        "capturedByUid" to capturedByUid, "capturedAtMillis" to capturedAtMillis,
        "latitude" to latitude, "longitude" to longitude, "remoteUrl" to remoteUrl, "uploaded" to uploaded
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toRoom(): InspectionRoom = InspectionRoom(
        id = getString("id") ?: id, propertyId = getString("propertyId").orEmpty(),
        name = getString("name").orEmpty(), sortOrder = getLong("sortOrder")?.toInt() ?: 0
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toItem(): ChecklistItem = ChecklistItem(
        id = getString("id") ?: id, roomId = getString("roomId").orEmpty(), propertyId = getString("propertyId").orEmpty(), name = getString("name").orEmpty(),
        moveInRating = getString("moveInRating")?.let { ConditionRating.from(it) }, moveInNote = getString("moveInNote").orEmpty(),
        moveInPhotoIds = stringList("moveInPhotoIds"), moveInTimestamp = getLong("moveInTimestamp"),
        moveOutRating = getString("moveOutRating")?.let { ConditionRating.from(it) }, moveOutNote = getString("moveOutNote").orEmpty(),
        moveOutPhotoIds = stringList("moveOutPhotoIds"), moveOutTimestamp = getLong("moveOutTimestamp")
    )
    
    private fun com.google.firebase.firestore.DocumentSnapshot.toSignature(): Signature = Signature(
        id = getString("id") ?: id, propertyId = getString("propertyId").orEmpty(), signerUid = getString("signerUid").orEmpty(),
        signerRole = UserRole.from(getString("signerRole")),
        phase = InspectionPhase.from(getString("phase")),
        pngBase64 = getString("pngBase64").orEmpty(),
        remoteUrl = getString("remoteUrl"),
        signedAtMillis = getLong("signedAtMillis") ?: System.currentTimeMillis(),
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toPhoto(): Photo = Photo(
        id = getString("id") ?: id, itemId = getString("itemId").orEmpty(), propertyId = getString("propertyId").orEmpty(),
        phase = InspectionPhase.from(getString("phase")), capturedByUid = getString("capturedByUid").orEmpty(),
        capturedAtMillis = getLong("capturedAtMillis") ?: System.currentTimeMillis(), latitude = getDouble("latitude"), longitude = getDouble("longitude"),
        remoteUrl = getString("remoteUrl"), uploaded = getBoolean("uploaded") ?: false
    )
}
