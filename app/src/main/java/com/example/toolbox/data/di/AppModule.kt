package com.example.toolbox.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.toolbox.data.local.AppDatabase
import com.example.toolbox.data.local.dao.CountdownDao
import com.example.toolbox.data.local.dao.CurrencyRateDao
import com.example.toolbox.data.local.dao.PasswordDao
import com.example.toolbox.data.local.datastore.IpCacheDataStore
import com.example.toolbox.data.local.datastore.PeriodDataStore
import com.example.toolbox.data.local.datastore.SettingsDataStore
import com.example.toolbox.data.remote.ExchangeRateApi
import com.example.toolbox.data.remote.IpApi
import com.example.toolbox.data.repository.CountdownRepository
import com.example.toolbox.data.repository.CurrencyRepository
import com.example.toolbox.data.repository.IpRepository
import com.example.toolbox.data.repository.PasswordRepository
import com.example.toolbox.data.security.CryptoHelper
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideGson(): Gson = Gson()

    @Provides @Singleton @Named("settings")
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })

    @Provides @Singleton @Named("ipCache")
    fun provideIpCacheStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("ip_cache") })

    @Provides @Singleton @Named("period")
    fun providePeriodStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("period") })

    @Provides @Singleton
    fun provideExchangeApi(): ExchangeRateApi =
        Retrofit.Builder()
            .baseUrl("https://open.er-api.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)

    @Provides @Singleton
    fun provideIpApi(): IpApi =
        Retrofit.Builder()
            .baseUrl("https://api.ip.sb/")
            .client(okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "Toolbox/1.0")
                        .build())
                }
                .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IpApi::class.java)

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "toolbox.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePasswordDao(db: AppDatabase): PasswordDao = db.passwordDao()
    @Provides fun provideCountdownDao(db: AppDatabase): CountdownDao = db.countdownDao()
    @Provides fun provideCurrencyRateDao(db: AppDatabase): CurrencyRateDao = db.currencyRateDao()

    @Provides @Singleton
    fun provideCryptoHelper(@ApplicationContext context: Context): CryptoHelper = CryptoHelper(context)

    @Provides @Singleton
    fun provideSettingsDataStore(@Named("settings") ds: DataStore<Preferences>): SettingsDataStore =
        SettingsDataStore(ds)

    @Provides @Singleton
    fun provideIpCacheDataStore(@Named("ipCache") ds: DataStore<Preferences>, gson: Gson): IpCacheDataStore =
        IpCacheDataStore(ds, gson)

    @Provides @Singleton
    fun providePasswordRepository(
        dao: PasswordDao,
        crypto: CryptoHelper,
    ): PasswordRepository = PasswordRepository(dao, crypto)

    @Provides @Singleton
    fun provideCountdownRepository(dao: CountdownDao): CountdownRepository = CountdownRepository(dao)

    @Provides @Singleton
    fun provideCurrencyRepository(
        dao: CurrencyRateDao,
        api: ExchangeRateApi,
        gson: Gson,
    ): CurrencyRepository = CurrencyRepository(dao, api, gson)

    @Provides @Singleton
    fun provideIpRepository(
        api: IpApi,
        cache: IpCacheDataStore,
    ): IpRepository = IpRepository(api, cache)

    @Provides @Singleton
    fun providePeriodDataStore(
        @Named("period") ds: DataStore<Preferences>,
        gson: Gson,
    ): PeriodDataStore = PeriodDataStore(ds, gson)
}
