package com.example.toolbox.data.remote

import com.example.toolbox.data.remote.model.ExchangeRateResponse
import com.example.toolbox.data.remote.model.IpInfoResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    /** open.er-api.com/v6/latest/{base} -> rates per 1 unit of [base]. */
    @GET("v6/latest/{base}")
    suspend fun latest(@Path("base") base: String): ExchangeRateResponse
}

interface IpApi {
    @GET("geoip")
    suspend fun myIp(): IpInfoResponse

    @GET("geoip/{ip}")
    suspend fun ipInfo(@Path("ip") ip: String): IpInfoResponse
}
