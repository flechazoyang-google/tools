package com.example.toolbox.data.local.dao

import androidx.room.*
import com.example.toolbox.data.local.entity.PasswordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {

    @Query("SELECT * FROM passwords ORDER BY isFavorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<PasswordEntity>>

    @Query(
        """SELECT * FROM passwords
        WHERE site LIKE '%' || :q || '%'
           OR account LIKE '%' || :q || '%'
           OR tag LIKE '%' || :q || '%'
        ORDER BY isFavorite DESC, createdAt DESC"""
    )
    fun search(q: String): Flow<List<PasswordEntity>>

    @Query("SELECT COUNT(*) FROM passwords")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PasswordEntity)

    @Update
    suspend fun update(entity: PasswordEntity)

    @Delete
    suspend fun delete(entity: PasswordEntity)
}
