package com.example.toolbox.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toolbox.data.repository.CurrencyRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Background exchange-rate refresh (24h, network-connected). Retrieves the repository via a
 * Hilt [EntryPoint] so we don't need a custom WorkManager factory.
 */
class CurrencyRefreshWorker @Inject constructor(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CurrencyEntryPoint {
        fun repository(): CurrencyRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val entry = EntryPointAccessors.fromApplication(applicationContext, CurrencyEntryPoint::class.java)
            entry.repository().refresh()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
