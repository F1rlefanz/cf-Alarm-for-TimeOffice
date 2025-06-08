package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data

import com.google.gson.annotations.SerializedName

/**
 * Represents a Philips Hue Bridge
 */
data class HueBridge(
    val id: String,
    val internalipaddress: String,
    val name: String? = null,
    val modelid: String? = null,
    val swversion: String? = null
)

/**
 * Bridge configuration response
 */
data class HueBridgeConfig(
    val name: String,
    val datastoreversion: String,
    val swversion: String,
    val apiversion: String,
    val mac: String,
    val bridgeid: String,
    val factorynew: Boolean,
    val replacesbridgeid: String?,
    val modelid: String,
    val whitelist: Map<String, HueUser>? = null
)

/**
 * Hue User/Application
 */
data class HueUser(
    @SerializedName("last use date")
    val lastUseDate: String,
    @SerializedName("create date")
    val createDate: String,
    val name: String
)

/**
 * Bridge discovery response
 */
data class BridgeDiscoveryResponse(
    val id: String,
    val internalipaddress: String,
    val port: Int? = null
)
