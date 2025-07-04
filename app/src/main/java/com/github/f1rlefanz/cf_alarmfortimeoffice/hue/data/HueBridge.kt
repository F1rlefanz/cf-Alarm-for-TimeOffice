package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * Represents a Philips Hue Bridge
 * @Immutable annotation optimizes Compose performance by preventing unnecessary recompositions
 */
@Immutable
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
@Immutable
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
@Immutable
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
@Immutable
data class BridgeDiscoveryResponse(
    val id: String,
    val internalipaddress: String,
    val port: Int? = null
)
