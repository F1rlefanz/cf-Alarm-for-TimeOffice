package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.repository

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Repository for Hue light operations and rule execution
 */
class HueLightRepository(
    private val bridgeRepository: HueBridgeRepository
) {
    
    /**
     * Execute a light action
     */
    suspend fun executeLightAction(action: HueLightAction): Result<Unit> {
        return try {
            when (action.targetType) {
                TargetType.LIGHT -> executeLightAction(action.targetId, action)
                TargetType.GROUP, TargetType.ROOM, TargetType.ZONE -> executeGroupAction(action.targetId, action)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error executing light action")
            Result.failure(e)
        }
    }
    
    private suspend fun executeLightAction(lightId: String, action: HueLightAction): Result<Unit> {
        val state = when (action.actionType) {
            ActionType.TURN_ON -> LightStateUpdate(
                on = true,
                bri = action.brightness,
                ct = action.colorTemperature,
                hue = action.color?.hue,
                sat = action.color?.saturation,
                xy = action.color?.xy,
                transitiontime = action.transitionTime
            )
            
            ActionType.TURN_OFF -> LightStateUpdate(
                on = false,
                transitiontime = action.transitionTime
            )
            
            ActionType.DIM -> LightStateUpdate(
                bri = action.brightness ?: 64, // 25% brightness
                transitiontime = action.transitionTime
            )
            
            ActionType.BRIGHTEN -> LightStateUpdate(
                bri = action.brightness ?: 254, // 100% brightness
                transitiontime = action.transitionTime
            )
            
            ActionType.SET_COLOR -> LightStateUpdate(
                hue = action.color?.hue,
                sat = action.color?.saturation,
                xy = action.color?.xy,
                transitiontime = action.transitionTime
            )
            
            ActionType.SET_TEMPERATURE -> LightStateUpdate(
                ct = action.colorTemperature,
                transitiontime = action.transitionTime
            )
            
            ActionType.PULSE -> LightStateUpdate(
                alert = "select"
            )
            
            ActionType.COLOR_LOOP -> LightStateUpdate(
                effect = "colorloop"
            )
        }
        
        val result = bridgeRepository.updateLightState(lightId, state)
        
        // Handle duration - revert after specified time
        if (result.isSuccess && action.duration != null) {
            scheduleRevert(lightId, action.duration)
        }
        
        return result
    }
    
    private suspend fun executeGroupAction(groupId: String, action: HueLightAction): Result<Unit> {
        val state = when (action.actionType) {
            ActionType.TURN_ON -> LightStateUpdate(
                on = true,
                bri = action.brightness,
                ct = action.colorTemperature,
                hue = action.color?.hue,
                sat = action.color?.saturation,
                xy = action.color?.xy,
                transitiontime = action.transitionTime
            )
            
            ActionType.TURN_OFF -> LightStateUpdate(
                on = false,
                transitiontime = action.transitionTime
            )
            
            ActionType.DIM -> LightStateUpdate(
                bri = action.brightness ?: 64,
                transitiontime = action.transitionTime
            )
            
            ActionType.BRIGHTEN -> LightStateUpdate(
                bri = action.brightness ?: 254,
                transitiontime = action.transitionTime
            )
            
            ActionType.SET_COLOR -> LightStateUpdate(
                hue = action.color?.hue,
                sat = action.color?.saturation,
                xy = action.color?.xy,
                transitiontime = action.transitionTime
            )
            
            ActionType.SET_TEMPERATURE -> LightStateUpdate(
                ct = action.colorTemperature,
                transitiontime = action.transitionTime
            )
            
            ActionType.PULSE -> LightStateUpdate(
                alert = "select"
            )
            
            ActionType.COLOR_LOOP -> LightStateUpdate(
                effect = "colorloop"
            )
        }
        
        return bridgeRepository.updateGroupAction(groupId, state)
    }
    
    private suspend fun scheduleRevert(targetId: String, durationMinutes: Int) {
        // In a real implementation, this would schedule a job
        // For now, we'll use a coroutine delay (not ideal for long durations)
        delay(durationMinutes * 60 * 1000L)
        
        // Turn off the light after duration
        bridgeRepository.updateLightState(targetId, LightStateUpdate(on = false))
    }
    
    /**
     * Convert RGB to Hue color values
     */
    fun rgbToHue(rgb: String): HueColor {
        val color = android.graphics.Color.parseColor(rgb)
        val r = android.graphics.Color.red(color) / 255f
        val g = android.graphics.Color.green(color) / 255f
        val b = android.graphics.Color.blue(color) / 255f
        
        // Convert to HSV
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (r * 255).roundToInt(),
            (g * 255).roundToInt(),
            (b * 255).roundToInt(),
            hsv
        )
        
        // Convert to Hue values
        val hue = (hsv[0] * 65535 / 360).roundToInt()
        val saturation = (hsv[1] * 254).roundToInt()
        
        // Also calculate XY for better color accuracy
        val xy = rgbToXY(r, g, b)
        
        return HueColor(
            hue = hue,
            saturation = saturation,
            xy = xy,
            rgb = rgb
        )
    }
    
    /**
     * Convert RGB to CIE XY color space
     */
    private fun rgbToXY(r: Float, g: Float, b: Float): List<Float> {
        // Apply gamma correction
        val red = if (r > 0.04045f) {
            Math.pow(((r + 0.055) / 1.055).toDouble(), 2.4).toFloat()
        } else {
            (r / 12.92f)
        }
        
        val green = if (g > 0.04045f) {
            Math.pow(((g + 0.055) / 1.055).toDouble(), 2.4).toFloat()
        } else {
            (g / 12.92f)
        }
        
        val blue = if (b > 0.04045f) {
            Math.pow(((b + 0.055) / 1.055).toDouble(), 2.4).toFloat()
        } else {
            (b / 12.92f)
        }
        
        // Convert to XYZ
        val X = red * 0.649926f + green * 0.103455f + blue * 0.197109f
        val Y = red * 0.234327f + green * 0.743075f + blue * 0.022598f
        val Z = red * 0.0000000f + green * 0.053077f + blue * 1.035763f
        
        // Convert to xy
        val sum = X + Y + Z
        return if (sum == 0f) {
            listOf(0f, 0f)
        } else {
            listOf(X / sum, Y / sum)
        }
    }
}
