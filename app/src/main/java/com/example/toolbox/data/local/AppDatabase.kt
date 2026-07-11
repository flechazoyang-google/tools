package com.example.toolbox.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.toolbox.data.local.dao.CountdownDao
import com.example.toolbox.data.local.dao.CurrencyRateDao
import com.example.toolbox.data.local.dao.PasswordDao
import com.example.toolbox.data.local.entity.CountdownEntity
import com.example.toolbox.data.local.entity.CurrencyRateEntity
import com.example.toolbox.data.local.entity.PasswordEntity

@Database(
    entities = [PasswordEntity::class, CountdownEntity::class, CurrencyRateEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
    abstract fun countdownDao(): CountdownDao
    abstract fun currencyRateDao(): CurrencyRateDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE countdowns ADD COLUMN isLunar INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE countdowns ADD COLUMN lunarMonth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE countdowns ADD COLUMN lunarDay INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
