package com.example.toolbox.data.repository

import com.example.toolbox.data.local.dao.CurrencyRateDao
import com.example.toolbox.data.local.entity.CurrencyRateEntity
import com.example.toolbox.data.remote.ExchangeRateApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CurrencyRepository @Inject constructor(
    private val dao: CurrencyRateDao,
    private val api: ExchangeRateApi,
    private val gson: Gson,
) {
    fun observeRates(): Flow<CurrencyRateEntity?> = dao.observe()
    suspend fun getCached(): CurrencyRateEntity? = dao.get()

    /** Refresh from network and persist. Returns the new entity, or null on failure. */
    suspend fun refresh(base: String = "USD"): CurrencyRateEntity? {
        return try {
            val resp = api.latest(base)
            if (resp.result == "success" && resp.rates != null) {
                val entity = CurrencyRateEntity(
                    id = 1,
                    base = resp.baseCode ?: base,
                    ratesJson = gson.toJson(resp.rates),
                    updatedAt = (resp.timeLastUpdateUnix ?: (System.currentTimeMillis() / 1000)) * 1000,
                )
                dao.insert(entity)
                entity
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getRateMap(entity: CurrencyRateEntity?): Map<String, Double> {
        if (entity == null) return emptyMap()
        return runCatching { gson.fromJson<Map<String, Double>>(entity.ratesJson, rateMapType) }
            .getOrDefault(emptyMap())
    }

    companion object {
        private val rateMapType = object : TypeToken<Map<String, Double>>() {}.type

        /** Convert [amount] from currency [from] to [to]. Rates are expressed per 1 unit of base (USD). */
        fun convert(amount: Double, from: String, to: String, rates: Map<String, Double>): Double? {
            val fromRate = rates[from] ?: return null
            val toRate = rates[to] ?: return null
            if (fromRate == 0.0) return null
            val usd = amount / fromRate
            return usd * toRate
        }
    }
}
