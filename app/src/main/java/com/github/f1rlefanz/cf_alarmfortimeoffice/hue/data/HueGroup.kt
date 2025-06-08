package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data

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
    val class: String? = null // Room class like "Living room", "Bedroom", etc.
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
    val all_on: Boolean,
    val any_on: Boolean
)

/**
 * Create/Update group request
 */
data class GroupUpdate(
    val name: String? = null,
    val lights: List<String>? = null,
    val class: String? = null
)
