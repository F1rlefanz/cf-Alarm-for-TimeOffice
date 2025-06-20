package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // Format and validate time input
                val formatted = formatTimeInput(newValue)
                onValueChange(formatted)
            },
            label = { Text(label) },
            placeholder = { Text("HH:MM") },
            isError = isError,
            supportingText = if (isError && errorMessage != null) {
                { Text(errorMessage) }
            } else null,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Time",
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Format time input to HH:MM pattern
 */
private fun formatTimeInput(input: String): String {
    // Remove all non-digits
    val digits = input.filter { it.isDigit() }
    
    return when (digits.length) {
        0 -> ""
        1 -> digits
        2 -> digits
        3 -> "${digits[0]}${digits[1]}:${digits[2]}"
        4 -> "${digits[0]}${digits[1]}:${digits[2]}${digits[3]}"
        else -> "${digits.take(2)}:${digits.drop(2).take(2)}"
    }
}

/**
 * Validate time format HH:MM
 */
fun isValidTimeFormat(time: String): Boolean {
    if (time.length != 5) return false
    
    val pattern = Pattern.compile("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")
    return pattern.matcher(time).matches()
}

/**
 * Validate time range
 */
fun isValidTimeRange(startTime: String, endTime: String): Boolean {
    if (!isValidTimeFormat(startTime) || !isValidTimeFormat(endTime)) return false
    
    try {
        val startParts = startTime.split(":")
        val endParts = endTime.split(":")
        
        val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
        val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
        
        return endMinutes > startMinutes
    } catch (e: Exception) {
        return false
    }
}
