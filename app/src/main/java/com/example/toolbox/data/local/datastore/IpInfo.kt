package com.example.toolbox.data.local.datastore

data class IpInfo(
    val ip: String,
    val country: String?,
    val region: String?,
    val city: String?,
    val org: String?,
    val version: String?,
    val cachedAt: Long = System.currentTimeMillis(),
)
