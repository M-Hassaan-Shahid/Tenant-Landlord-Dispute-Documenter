package com.example.tenant_landlorddisputedocumenter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tenant_landlorddisputedocumenter.data.local.entity.PropertyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(property: PropertyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(properties: List<PropertyEntity>)

    @Query("SELECT * FROM properties WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PropertyEntity?

    @Query("SELECT * FROM properties WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<PropertyEntity?>

    @Query("SELECT * FROM properties WHERE landlordId = :uid OR tenantId = :uid ORDER BY updatedAtMillis DESC")
    fun observeForUser(uid: String): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE inviteCode = :code LIMIT 1")
    suspend fun findByInviteCode(code: String): PropertyEntity?

    @Query("DELETE FROM properties WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        DELETE FROM properties
        WHERE (landlordId = :uid OR tenantId = :uid) AND id NOT IN (:keepIds)
        """
    )
    suspend fun deleteForUserExcept(uid: String, keepIds: List<String>)

    @Query("SELECT id FROM properties WHERE landlordId = :uid OR tenantId = :uid")
    suspend fun listIdsForUser(uid: String): List<String>

    @Query("DELETE FROM properties WHERE landlordId = :uid OR tenantId = :uid")
    suspend fun deleteAllForUser(uid: String)
}
