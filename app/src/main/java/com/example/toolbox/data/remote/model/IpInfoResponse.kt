package com.example.toolbox.data.remote.model

data class IpInfoResponse(
    val ip: String?,
    val country: String?,
    @com.google.gson.annotations.SerializedName("country_code") val countryCode: String?,
    val region: String?,
    val city: String?,
    val isp: String?,
    val organization: String?,
)
