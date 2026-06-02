package com.example.tenant_landlorddisputedocumenter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tenant_landlorddisputedocumenter.data.local.entity.DisputeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DisputeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dispute: DisputeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(disputes: List<DisputeEntity>)

    @Query("SELECT * FROM disputes WHERE propertyId = :propertyId ORDER BY raisedAtMillis DESC")
    fun observeForProperty(propertyId: String): Flow<List<DisputeEntity>>

    @Query("SELECT * FROM disputes WHERE itemId = :itemId ORDER BY raisedAtMillis DESC")
    fun observeForItem(itemId: String): Flow<List<DisputeEntity>>

    @Query("SELECT * FROM disputes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DisputeEntity?

    @Query("DELETE FROM disputes WHERE propertyId = :propertyId")
    suspend fun deleteForProperty(propertyId: String)

    @Query("SELECT id FROM disputes WHERE propertyId = :propertyId")
    suspend fun listIdsForProperty(propertyId: String): List<String>

    @Query("DELETE FROM disputes WHERE id = :id")
    suspend fun delete(id: String)
}
