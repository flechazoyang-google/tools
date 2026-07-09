package com.example.toolbox.data.repository

import com.example.toolbox.data.local.datastore.IpCacheDataStore
import com.example.toolbox.data.local.datastore.IpInfo
import com.example.toolbox.data.remote.IpApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class IpRepository @Inject constructor(
    private val api: IpApi,
    private val cache: IpCacheDataStore,
) {
    fun observeCache(): Flow<List<IpInfo>> = cache.observe()

    /** Look up an IP (blank = current public IP). On success the result is cached locally. */
    suspend fun lookup(ip: String?): Result<IpInfo> {
        return try {
            val resp = if (ip.isNullOrBlank()) api.myIp() else api.ipInfo(ip)
            // api.ip.sb returns HTTP 4xx on error, which throws an exception caught below

            val info = IpInfo(
                ip = resp.ip ?: ip ?: "",
                country = resp.country,
                region = resp.region,
                city = resp.city,
                org = resp.organization ?: resp.isp,
                version = if ((resp.ip ?: "").contains(':')) "IPv6" else "IPv4",
            )
            cache.put(info)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCached(ip: String): IpInfo? = cache.get(ip)
}
