package com.example.toolbox

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.toolbox.data.worker.CurrencyRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class ToolboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleCurrencySync(this)
    }
}

private fun scheduleCurrencySync(context: Context) {
    val request = PeriodicWorkRequestBuilder<CurrencyRefreshWorker>(24, TimeUnit.HOURS)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "currency_sync",
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
