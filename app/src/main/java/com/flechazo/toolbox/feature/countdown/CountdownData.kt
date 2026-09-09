package com.flechazo.toolbox.feature.countdown

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Entity(tableName = "countdown_events")
data class CountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** yyyy-MM-dd */
    val date: String,
    /** 0 = countdown to, 1 = anniversary since */
    val type: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdown_events ORDER BY date ASC")
    fun observeAll(): Flow<List<CountdownEntity>>

    @Insert
    suspend fun insert(entity: CountdownEntity)

    @Query("DELETE FROM countdown_events WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [CountdownEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun countdownDao(): CountdownDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "toolbox.db").build()

    @Provides
    fun provideCountdownDao(db: AppDatabase): CountdownDao = db.countdownDao()
}

@Singleton
class CountdownRepository @Inject constructor(private val dao: CountdownDao) {
    fun observeAll(): Flow<List<CountdownEntity>> = dao.observeAll()
    suspend fun add(title: String, date: String, type: Int) =
        dao.insert(CountdownEntity(title = title, date = date, type = type))
    suspend fun delete(id: Long) = dao.delete(id)
}
