package com.example.tenant_landlorddisputedocumenter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tenant_landlorddisputedocumenter.data.local.entity.SignatureEntity
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import kotlinx.coroutines.flow.Flow

@Dao
interface SignatureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(signature: SignatureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(signatures: List<SignatureEntity>)

    @Query("SELECT * FROM signatures WHERE propertyId = :propertyId AND phase = :phase")
    fun observeForPhase(propertyId: String, phase: InspectionPhase): Flow<List<SignatureEntity>>

    @Query("SELECT * FROM signatures WHERE propertyId = :propertyId")
    fun observeForProperty(propertyId: String): Flow<List<SignatureEntity>>

    @Query("SELECT COUNT(*) FROM signatures WHERE propertyId = :propertyId AND phase = :phase AND signerUid = :uid")
    suspend fun countFor(propertyId: String, phase: InspectionPhase, uid: String): Int

    @Query("SELECT * FROM signatures WHERE propertyId = :propertyId")
    suspend fun listForProperty(propertyId: String): List<SignatureEntity>

    @Query("DELETE FROM signatures WHERE propertyId = :propertyId")
    suspend fun deleteForProperty(propertyId: String)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun delete(id: String)
}
