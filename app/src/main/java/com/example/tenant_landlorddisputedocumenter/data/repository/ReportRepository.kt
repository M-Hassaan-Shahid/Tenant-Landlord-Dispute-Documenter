package com.example.tenant_landlorddisputedocumenter.data.repository

import android.content.Context
import com.example.tenant_landlorddisputedocumenter.data.local.dao.DisputeDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.ItemDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.PhotoDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.PropertyDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.RoomDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.SignatureDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.UserDao
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.util.PdfReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Assembles the data graph needed for a PDF and hands it off to [PdfReportGenerator].
 */
class ReportRepository(
    private val context: Context,
    private val propertyDao: PropertyDao,
    private val roomDao: RoomDao,
    private val itemDao: ItemDao,
    private val photoDao: PhotoDao,
    private val signatureDao: SignatureDao,
    private val disputeDao: DisputeDao,
    private val userDao: UserDao,
) {

    suspend fun validateReportReady(propertyId: String) {
        val property = propertyDao.get(propertyId)?.toDomain() ?: error("Property not found.")
        val tenantId = property.tenantId ?: error("Property has no tenant.")
        when (property.status) {
            PropertyStatus.CLOSED -> Unit
            PropertyStatus.MOVE_OUT -> {
                require(hasBothSignatures(propertyId, InspectionPhase.MOVE_OUT, property.landlordId, tenantId)) {
                    "Both parties must sign move-out before generating the report."
                }
            }
            else -> error("Report is available after move-out is signed by both parties, or once the property is closed.")
        }
        require(hasBothSignatures(propertyId, InspectionPhase.MOVE_IN, property.landlordId, tenantId)) {
            "Both parties must sign move-in before generating the report."
        }
    }

    suspend fun generateReport(propertyId: String): File? = withContext(Dispatchers.IO) {
        validateReportReady(propertyId)
        val property = propertyDao.get(propertyId)?.toDomain() ?: return@withContext null
        val rooms = roomDao.listForProperty(propertyId).map { it.toDomain() }

        val items = runCatching { itemDao.observeForProperty(propertyId).first() }.getOrDefault(emptyList())
        val itemsDomain = items.map { it.toDomain() }
        val photoIds = itemsDomain.flatMap { it.moveInPhotoIds + it.moveOutPhotoIds }.distinct()
        val photos = photoDao.getMany(photoIds).map { it.toDomain() }
        val signatures = runCatching { signatureDao.observeForProperty(propertyId).first() }
            .getOrDefault(emptyList()).map { it.toDomain() }
        val disputes = runCatching { disputeDao.observeForProperty(propertyId).first() }
            .getOrDefault(emptyList()).map { it.toDomain() }

        val landlord = userDao.get(property.landlordId)?.toDomain()
        val tenant = property.tenantId?.let { userDao.get(it)?.toDomain() }

        val data = PdfReportGenerator.ReportData(
            property = property,
            landlordName = landlord?.displayName ?: "Landlord",
            tenantName = tenant?.displayName ?: "Tenant",
            rooms = rooms,
            items = itemsDomain,
            photosById = photos.associateBy { it.id },
            signatures = signatures,
            disputes = disputes,
        )

        PdfReportGenerator(context).generate(data)
    }

    private suspend fun hasBothSignatures(
        propertyId: String,
        phase: InspectionPhase,
        landlordId: String,
        tenantId: String,
    ): Boolean {
        val signatures = signatureDao.listForProperty(propertyId)
            .filter { it.phase == phase }
        return signatures.any { it.signerUid == landlordId } &&
            signatures.any { it.signerUid == tenantId }
    }
}
