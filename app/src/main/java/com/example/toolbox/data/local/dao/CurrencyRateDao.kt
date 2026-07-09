package com.example.toolbox.data.local.dao

import androidx.room.*
import com.example.toolbox.data.local.entity.CurrencyRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyRateDao {

    @Query("SELECT * FROM currency_rates WHERE id = 1")
    suspend fun get(): CurrencyRateEntity?

    @Query("SELECT * FROM currency_rates WHERE id = 1")
    fun observe(): Flow<CurrencyRateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CurrencyRateEntity)
}
