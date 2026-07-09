package com.example.toolbox.data.local.dao

import androidx.room.*
import com.example.toolbox.data.local.entity.CountdownEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {

    @Query("SELECT * FROM countdowns ORDER BY isPinned DESC, targetDate ASC")
    fun observeAll(): Flow<List<CountdownEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CountdownEntity)

    @Update
    suspend fun update(entity: CountdownEntity)

    @Delete
    suspend fun delete(entity: CountdownEntity)
}
