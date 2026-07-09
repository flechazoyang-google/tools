package com.example.toolbox.data.remote.model

import com.google.gson.annotations.SerializedName

data class ExchangeRateResponse(
    val result: String?,
    @SerializedName("base_code") val baseCode: String?,
    @SerializedName("time_last_update_unix") val timeLastUpdateUnix: Long?,
    val rates: Map<String, Double>?,
)
