package com.example.tenant_landlorddisputedocumenter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tenant_landlorddisputedocumenter.data.local.entity.PhotoEntity
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(photos: List<PhotoEntity>)

    @Query("SELECT * FROM photos WHERE itemId = :itemId AND phase = :phase ORDER BY capturedAtMillis ASC")
    fun observeForItem(itemId: String, phase: InspectionPhase): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE uploaded = 0")
    suspend fun pendingUploads(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PhotoEntity?

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM photos WHERE itemId IN (:itemIds)")
    suspend fun deleteForItems(itemIds: List<String>)

    @Query("SELECT * FROM photos WHERE propertyId = :propertyId")
    suspend fun listForProperty(propertyId: String): List<PhotoEntity>

    @Query("DELETE FROM photos WHERE propertyId = :propertyId")
    suspend fun deleteForProperty(propertyId: String)
}
