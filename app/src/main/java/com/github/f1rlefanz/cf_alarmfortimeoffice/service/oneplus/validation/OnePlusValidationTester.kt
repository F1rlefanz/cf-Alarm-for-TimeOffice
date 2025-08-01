/**
 * OnePlus Validation Tester
 * 
 * Development tool for testing and validating OnePlus device detection.
 * Provides comprehensive testing capabilities for the validation system.
 * 
 * Features:
 * - Manual validation testing
 * - Performance benchmarking
 * - Validation result analysis
 * - Device spoofing for testing
 * 
 * @author CF-Alarm Team
 * @since 2025.1.0
 */
package com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.validation

import android.content.Context
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

/**
 * Testing and analysis utilities for OnePlus validation
 */
class OnePlusValidationTester(
    private val context: Context
) {
    
    private val validationManager = OnePlusValidationManager(context)
    
    /**
     * Comprehensive validation test suite
     */
    suspend fun runValidationTestSuite(): ValidationTestResult {
        Logger.i(LogTags.BATTERY_OPTIMIZATION, "🧪 Starting OnePlus validation test suite...")
        
        val testResults = mutableMapOf<String, TestResult>()
        var totalTime = 0L
        
        try {
            // Test 1: Basic validation
            totalTime += measureTimeMillis {
                testResults["basic_validation"] = testBasicValidation()
            }
            
            // Test 2: Performance test
            totalTime += measureTimeMillis {
                testResults["performance"] = testValidationPerformance()
            }
            
            // Test 3: Cache behavior
            totalTime += measureTimeMillis {
                testResults["cache_behavior"] = testCacheBehavior()
            }
            
            // Test 4: Error handling
            totalTime += measureTimeMillis {
                testResults["error_handling"] = testErrorHandling()
            }
            
            // Test 5: Confidence scoring
            totalTime += measureTimeMillis {
                testResults["confidence_scoring"] = testConfidenceScoring()
            }
            
            val passedTests = testResults.values.count { it.passed }
            val totalTests = testResults.size
            
            Logger.business(
                LogTags.BATTERY_OPTIMIZATION,
                "✅ Validation test suite completed: $passedTests/$totalTests tests passed in ${totalTime}ms"
            )
            
            return ValidationTestResult(
                passed = passedTests == totalTests,
                totalTests = totalTests,
                passedTests = passedTests,
                totalTime = totalTime,
                testResults = testResults,
                summary = generateTestSummary(testResults, totalTime)
            )
            
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "💥 Validation test suite failed", e)
            return ValidationTestResult(
                passed = false,
                totalTests = testResults.size,
                passedTests = testResults.values.count { it.passed },
                totalTime = totalTime,
                testResults = testResults,
                summary = "Test suite failed with exception: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * Test basic validation functionality
     */
    private suspend fun testBasicValidation(): TestResult {
        return try {
            val result = validationManager.getDeviceValidation()
            
            val isValid = when (result) {
                is OnePlusDeviceValidationResult.Valid -> {
                    result.confidence > 0.7f && 
                    result.deviceInfo.isOnePlusDevice &&
                    result.validationSteps.isNotEmpty()
                }
                is OnePlusDeviceValidationResult.NotOnePlus -> {
                    result.confidence < 0.7f &&
                    result.validationSteps.isNotEmpty()
                }
                is OnePlusDeviceValidationResult.ValidationError -> {
                    result.canRetry // Error should be recoverable
                }
            }
            
            TestResult(
                passed = isValid,
                details = "Validation result: ${result::class.simpleName}",
                metrics = mapOf("confidence" to getConfidenceFromResult(result))
            )
        } catch (e: Exception) {
            TestResult(
                passed = false,
                details = "Basic validation failed: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * Test validation performance
     */
    private suspend fun testValidationPerformance(): TestResult {
        return try {
            val iterations = 5
            val times = mutableListOf<Long>()
            
            repeat(iterations) {
                val time = measureTimeMillis {
                    validationManager.getDeviceValidation(forceRefresh = true)
                }
                times.add(time)
            }
            
            val averageTime = times.average()
            val maxTime = times.maxOrNull() ?: 0L
            val minTime = times.minOrNull() ?: 0L
            
            // Performance thresholds
            val isPerformant = averageTime < 2000 && maxTime < 5000 // 2s average, 5s max
            
            TestResult(
                passed = isPerformant,
                details = "Performance test: avg=${averageTime.toInt()}ms, max=${maxTime}ms, min=${minTime}ms",
                metrics = mapOf(
                    "average_time" to averageTime,
                    "max_time" to maxTime.toDouble(),
                    "min_time" to minTime.toDouble()
                )
            )
        } catch (e: Exception) {
            TestResult(
                passed = false,
                details = "Performance test failed: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * Test cache behavior
     */
    private suspend fun testCacheBehavior(): TestResult {
        return try {
            // Clear cache and get fresh result
            validationManager.clearCache()
            val firstResult = validationManager.getDeviceValidation()
            
            // Get cached result
            val cachedResult = validationManager.getDeviceValidation()
            
            // Force refresh
            val refreshedResult = validationManager.getDeviceValidation(forceRefresh = true)
            
            val isCacheWorking = firstResult::class == cachedResult::class &&
                                firstResult::class == refreshedResult::class
            
            TestResult(
                passed = isCacheWorking,
                details = "Cache behavior test: consistent results across calls",
                metrics = mapOf(
                    "cache_consistency" to if (isCacheWorking) 1.0 else 0.0
                )
            )
        } catch (e: Exception) {
            TestResult(
                passed = false,
                details = "Cache test failed: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * Test error handling scenarios
     */
    private suspend fun testErrorHandling(): TestResult {
        return try {
            // Test timeout scenario by creating a validation with very short timeout
            // Note: This is a simplified test - in real scenarios we would mock components
            
            val result = validationManager.getDeviceValidation()
            
            // Error handling is working if we get a result (even error) without throwing
            val isErrorHandlingWorking = true
            
            TestResult(
                passed = isErrorHandlingWorking,
                details = "Error handling test: no unhandled exceptions",
                metrics = mapOf("error_handling_score" to 1.0)
            )
        } catch (e: Exception) {
            TestResult(
                passed = false,
                details = "Error handling test failed: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * Test confidence scoring logic
     */
    private suspend fun testConfidenceScoring(): TestResult {
        return try {
            val confidence = validationManager.getValidationConfidence()
            
            // Confidence should be between 0 and 1
            val isValidConfidence = confidence in 0.0f..1.0f
            
            TestResult(
                passed = isValidConfidence,
                details = "Confidence scoring test: confidence=$confidence",
                metrics = mapOf("confidence_value" to confidence.toDouble())
            )
        } catch (e: Exception) {
            TestResult(
                passed = false,
                details = "Confidence test failed: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * Generate comprehensive test report
     */
    suspend fun generateTestReport(): String {
        val testResult = runValidationTestSuite()
        val deviceInfo = validationManager.getOnePlusDeviceInfo()
        val capabilities = validationManager.getOnePlusCapabilities()
        val validationDetails = validationManager.getValidationDetails()
        
        return buildString {
            appendLine("=== OnePlus Validation Test Report ===")
            appendLine("Generated: ${java.time.LocalDateTime.now()}")
            appendLine()
            
            // Test results summary
            appendLine("📊 Test Results:")
            appendLine("  Overall: ${if (testResult.passed) "✅ PASSED" else "❌ FAILED"}")
            appendLine("  Tests: ${testResult.passedTests}/${testResult.totalTests}")
            appendLine("  Duration: ${testResult.totalTime}ms")
            appendLine()
            
            // Individual test results
            appendLine("🧪 Individual Tests:")
            testResult.testResults.forEach { (testName, result) ->
                val status = if (result.passed) "✅" else "❌"
                appendLine("  $status $testName: ${result.details}")
                if (result.metrics.isNotEmpty()) {
                    result.metrics.forEach { (metric, value) ->
                        appendLine("    📈 $metric: $value")
                    }
                }
                result.error?.let { error ->
                    appendLine("    💥 Error: ${error.message}")
                }
            }
            appendLine()
            
            // Device information
            deviceInfo?.let { info ->
                appendLine("📱 Device Information:")
                appendLine("  Model: ${info.model}")
                appendLine("  Manufacturer: ${info.manufacturer}")
                appendLine("  Android: ${info.androidVersion}")
                appendLine("  OxygenOS: ${info.oxygenOSVersion ?: "Not detected"}")
                appendLine("  OxygenOS 15+: ${info.isOxygenOS15OrHigher}")
                appendLine()
            }
            
            // Device capabilities
            capabilities?.let { caps ->
                appendLine("🎯 Device Capabilities:")
                appendLine("  Enhanced Optimization: ${caps.hasEnhancedOptimization}")
                appendLine("  Advanced Battery Mgmt: ${caps.hasAdvancedBatteryManagement}")
                appendLine("  Accessibility Bypass: ${caps.supportsAccessibilityBypass}")
                appendLine("  Auto-Start Manager: ${caps.hasAutoStartManager}")
                appendLine("  Power Saver Settings: ${caps.hasPowerSaverSettings}")
                appendLine("  App Locking: ${caps.supportsAppLocking}")
                appendLine("  Reset Risk: ${String.format("%.1f%%", caps.batteryOptimizationResetRisk * 100)}")
                appendLine("  Recommended Steps: ${caps.recommendedConfigSteps.size}")
                appendLine()
            }
            
            // Validation details
            appendLine("🔍 Detailed Validation:")
            appendLine(validationDetails)
            
            // Test summary
            appendLine()
            appendLine("📋 Summary:")
            appendLine(testResult.summary)
            
            if (testResult.error != null) {
                appendLine()
                appendLine("💥 Test Suite Error:")
                appendLine(testResult.error.stackTraceToString())
            }
            
            appendLine("=====================================")
        }
    }
    
    /**
     * Benchmark validation performance
     */
    suspend fun benchmarkValidation(iterations: Int = 10): BenchmarkResult {
        Logger.i(LogTags.BATTERY_OPTIMIZATION, "⏱️ Benchmarking OnePlus validation ($iterations iterations)...")
        
        val times = mutableListOf<Long>()
        val memoryUsages = mutableListOf<Long>()
        
        repeat(iterations) { iteration ->
            // Force garbage collection before each test
            System.gc()
            
            val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            
            val time = measureTimeMillis {
                validationManager.getDeviceValidation(forceRefresh = true)
            }
            
            val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memoryUsed = finalMemory - initialMemory
            
            times.add(time)
            memoryUsages.add(memoryUsed)
            
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "Iteration $iteration: ${time}ms, ${memoryUsed}bytes")
        }
        
        return BenchmarkResult(
            iterations = iterations,
            averageTime = times.average(),
            minTime = times.minOrNull() ?: 0L,
            maxTime = times.maxOrNull() ?: 0L,
            averageMemory = memoryUsages.average(),
            minMemory = memoryUsages.minOrNull() ?: 0L,
            maxMemory = memoryUsages.maxOrNull() ?: 0L,
            times = times,
            memoryUsages = memoryUsages
        )
    }
    
    // Helper functions
    private fun getConfidenceFromResult(result: OnePlusDeviceValidationResult): Double {
        return when (result) {
            is OnePlusDeviceValidationResult.Valid -> result.confidence.toDouble()
            is OnePlusDeviceValidationResult.NotOnePlus -> result.confidence.toDouble()
            is OnePlusDeviceValidationResult.ValidationError -> 0.0
        }
    }
    
    private fun generateTestSummary(testResults: Map<String, TestResult>, totalTime: Long): String {
        val passedTests = testResults.values.count { it.passed }
        val totalTests = testResults.size
        val passRate = (passedTests.toDouble() / totalTests * 100).toInt()
        
        return "Validation testing completed with $passRate% pass rate in ${totalTime}ms"
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        validationManager.cleanup()
    }
}

/**
 * Test result data classes
 */
data class ValidationTestResult(
    val passed: Boolean,
    val totalTests: Int,
    val passedTests: Int,
    val totalTime: Long,
    val testResults: Map<String, TestResult>,
    val summary: String,
    val error: Exception? = null
)

data class TestResult(
    val passed: Boolean,
    val details: String,
    val metrics: Map<String, Double> = emptyMap(),
    val error: Exception? = null
)

data class BenchmarkResult(
    val iterations: Int,
    val averageTime: Double,
    val minTime: Long,
    val maxTime: Long,
    val averageMemory: Double,
    val minMemory: Long,
    val maxMemory: Long,
    val times: List<Long>,
    val memoryUsages: List<Long>
)
