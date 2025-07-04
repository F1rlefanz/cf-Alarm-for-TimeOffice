package com.github.f1rlefanz.cf_alarmfortimeoffice.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import com.github.f1rlefanz.cf_alarmfortimeoffice.BuildConfig

/**
 * Performance Utilities für die CF-Alarm App
 * 
 * Hilft bei der Identifikation von Performance-Problemen
 */
object PerformanceUtils {
    
    /**
     * Tracked Recompositions für Debug-Zwecke
     * Nur in Debug-Builds aktiv
     */
    @Composable
    fun TrackRecomposition(tag: String) {
        if (BuildConfig.DEBUG) {
            val count = remember { mutableStateMapOf<String, Int>() }
            SideEffect {
                count[tag] = (count[tag] ?: 0) + 1
                Logger.v(LogTags.PERFORMANCE, "Recomposition: $tag (${count[tag]}x)")
            }
        }
    }
    
    /**
     * Misst die Zeit für eine Operation
     */
    inline fun <T> measureTimeMillis(tag: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        if (duration > 100) {
            Logger.w(LogTags.PERFORMANCE, "Operation '$tag' took ${duration}ms")
        }
        return result
    }
}
