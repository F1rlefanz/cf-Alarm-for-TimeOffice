package com.github.f1rlefanz.cf_alarmfortimeoffice.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import com.github.f1rlefanz.cf_alarmfortimeoffice.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Performance Utilities für die CF-Alarm App
 * 
 * Hilft bei der Identifikation von Performance-Problemen und UI Thread Optimierung
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
    
    /**
     * UI THREAD OPTIMIZATION: Yields control to UI thread during heavy operations
     * Verhindert das Droppen von Frames bei langen Operationen
     */
    suspend fun yieldToUI() {
        yield() // Let UI thread process
    }
    
    /**
     * UI THREAD OPTIMIZATION: Performs heavy computation on background thread
     * Automatically switches context und yields to UI thread
     */
    suspend fun <T> computeOffMainThread(computation: () -> T): T {
        return withContext(Dispatchers.Default) {
            computation()
        }
    }
    
    /**
     * UI THREAD OPTIMIZATION: Processes large lists in chunks to prevent ANR
     * Yields control between chunks for better UI responsiveness
     */
    suspend fun <T> processListInChunks(
        list: List<T>, 
        chunkSize: Int = 50,
        processor: (T) -> Unit
    ) {
        list.chunked(chunkSize).forEach { chunk ->
            chunk.forEach(processor)
            yieldToUI() // Yield after each chunk
        }
    }
    
    /**
     * UI THREAD OPTIMIZATION: Batches UI updates to prevent excessive recompositions
     * @param delayMs Delay between batches (default 16ms = 1 frame at 60fps)
     */
    suspend fun batchUIUpdates(delayMs: Long = 16L) {
        kotlinx.coroutines.delay(delayMs)
    }
}
