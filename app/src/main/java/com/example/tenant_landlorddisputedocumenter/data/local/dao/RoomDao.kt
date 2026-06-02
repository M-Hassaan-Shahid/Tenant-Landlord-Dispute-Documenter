package com.example.tenant_landlorddisputedocumenter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tenant_landlorddisputedocumenter.data.local.entity.RoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(room: RoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rooms: List<RoomEntity>)

    @Query("SELECT * FROM rooms WHERE propertyId = :propertyId ORDER BY sortOrder ASC")
    fun observeForProperty(propertyId: String): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE propertyId = :propertyId ORDER BY sortOrder ASC")
    suspend fun listForProperty(propertyId: String): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoomEntity?

    @Query("DELETE FROM rooms WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM rooms WHERE propertyId = :propertyId")
    suspend fun deleteForProperty(propertyId: String)
}
