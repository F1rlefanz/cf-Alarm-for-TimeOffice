package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data

import com.google.gson.annotations.SerializedName

/**
 * Represents a Hue Group (Room, Zone, etc.)
 */
data class HueGroup(
    val id: String,
    val name: String,
    val lights: List<String>,
    val type: String, // "Room", "Zone", "Entertainment", "LightGroup"
    val action: GroupAction,
    val state: GroupState? = null,
    val recycle: Boolean = false,
    @SerializedName("class")
    val roomClass: String? = null // Room class like "Living room", "Bedroom", etc.
)

/**
 * Group Action State
 */
data class GroupAction(
    val on: Boolean,
    val bri: Int? = null,
    val hue: Int? = null,
    val sat: Int? = null,
    val xy: List<Float>? = null,
    val ct: Int? = null,
    val alert: String? = null,
    val effect: String? = null,
    val colormode: String? = null
)

/**
 * Group State
 */
data class GroupState(
    @SerializedName("all_on")
    val allOn: Boolean,
    @SerializedName("any_on")
    val anyOn: Boolean
)

/**
 * Create/Update group request
 */
data class GroupUpdate(
    val name: String? = null,
    val lights: List<String>? = null,
    @SerializedName("class")
    val roomClass: String? = null
)
