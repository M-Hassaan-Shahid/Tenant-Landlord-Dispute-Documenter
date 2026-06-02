package com.example.tenant_landlorddisputedocumenter.data.repository

import android.content.Context
import com.example.tenant_landlorddisputedocumenter.data.local.dao.DisputeDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.ItemDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.PhotoDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.PropertyDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.RoomDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.SignatureDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.UserDao
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

    suspend fun generateReport(propertyId: String): File? = withContext(Dispatchers.IO) {
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
}
