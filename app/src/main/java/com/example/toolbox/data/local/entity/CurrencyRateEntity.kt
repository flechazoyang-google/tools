package com.example.toolbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached exchange rates. Single row (id = 1). [ratesJson] is a JSON map of
 * "CURRENCY" -> rate-per-1-USD. [updatedAt] is epoch millis of the last successful fetch.
 */
@Entity(tableName = "currency_rates")
data class CurrencyRateEntity(
    @PrimaryKey val id: Int = 1,
    val base: String,
    val ratesJson: String,
    val updatedAt: Long,
)
