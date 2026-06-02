package com.example.tenant_landlorddisputedocumenter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tenant_landlorddisputedocumenter.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("SELECT * FROM items WHERE roomId = :roomId ORDER BY name ASC")
    fun observeForRoom(roomId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE propertyId = :propertyId ORDER BY roomId, name")
    fun observeForProperty(propertyId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE roomId = :roomId")
    suspend fun listForRoom(roomId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE propertyId = :propertyId")
    suspend fun listForProperty(propertyId: String): List<ItemEntity>

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM items WHERE roomId = :roomId")
    suspend fun deleteForRoom(roomId: String)

    @Query("DELETE FROM items WHERE propertyId = :propertyId")
    suspend fun deleteForProperty(propertyId: String)
}
