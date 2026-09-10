package com.flechazo.toolbox.feature.countdown

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {

    /**
     * 不在 SQL 里排序：`nextOccurrence` 依赖农历换算与重复规则，SQL 算不出来。
     * 排序统一交给 [CountdownEngine.sorted]（事件量级为几十条）。
     */
    @Query("SELECT * FROM countdown_events")
    fun observeAll(): Flow<List<CountdownEntity>>

    @Query("SELECT * FROM countdown_events ORDER BY id ASC")
    fun observeAllOrderedById(): Flow<List<CountdownEntity>>

    @Query("SELECT * FROM countdown_events WHERE id = :id")
    suspend fun byId(id: Long): CountdownEntity?

    @Query("SELECT * FROM countdown_events")
    suspend fun snapshot(): List<CountdownEntity>

    @Insert
    suspend fun insert(entity: CountdownEntity): Long

    @Update
    suspend fun update(entity: CountdownEntity)

    @Upsert
    suspend fun upsert(entity: CountdownEntity)

    @Delete
    suspend fun delete(entity: CountdownEntity)

    @Query("DELETE FROM countdown_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM countdown_events")
    suspend fun clear()
}
