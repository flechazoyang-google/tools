package com.example.toolbox.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.toolbox.data.local.dao.CountdownDao
import com.example.toolbox.data.local.dao.CurrencyRateDao
import com.example.toolbox.data.local.dao.PasswordDao
import com.example.toolbox.data.local.entity.CountdownEntity
import com.example.toolbox.data.local.entity.CurrencyRateEntity
import com.example.toolbox.data.local.entity.PasswordEntity

@Database(
    entities = [PasswordEntity::class, CountdownEntity::class, CurrencyRateEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
    abstract fun countdownDao(): CountdownDao
    abstract fun currencyRateDao(): CurrencyRateDao
}
