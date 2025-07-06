package com.github.f1rlefanz.cf_alarmfortimeoffice.util

/**
 * Legacy Constants Import File
 * 
 * REFACTORED: Constants wurden in thematische Dateien aufgeteilt für bessere Wartbarkeit.
 * Diese Datei bietet Rückwärtskompatibilität durch Re-Export der aufgeteilten Constants.
 * 
 * Neue Organisation:
 * - UIConstants, SpacingConstants, AlphaValues, etc. → util.theme.UIConstants
 * - TimingConstants, NetworkConstants, AnimationDurations → util.timing.TimingConstants  
 * - AlarmConstants, ShiftConstants, CalendarConstants → util.business.BusinessConstants
 * - UIText → util.text.UITextConstants
 */

// Re-export für Rückwärtskompatibilität
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.SpacingConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.LayoutFractions
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.AlphaValues
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.FontSizes
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.BorderConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.UIColors
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.GraphicsConstants

import com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.UIConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.AnimationDurations
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.NetworkConstants

import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.CalendarConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.AlarmConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.ShiftConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.StorageConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.DateTimeFormats
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.AppConstants

import com.github.f1rlefanz.cf_alarmfortimeoffice.util.text.UIText

// Legacy object re-exports für vollständige Rückwärtskompatibilität
// Diese können in zukünftigen Versionen entfernt werden

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.CalendarConstants instead")
typealias CalendarConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.CalendarConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.NetworkConstants instead")
typealias NetworkConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.NetworkConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.UIConstants instead")
typealias UIConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.UIConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.SpacingConstants instead")
typealias SpacingConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.SpacingConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.AlarmConstants instead")
typealias AlarmConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.AlarmConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.ShiftConstants instead")
typealias ShiftConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.ShiftConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.StorageConstants instead")
typealias StorageConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.StorageConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.DateTimeFormats instead")
typealias DateTimeFormats = com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.DateTimeFormats

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.text.UIText instead")
typealias UIText = com.github.f1rlefanz.cf_alarmfortimeoffice.util.text.UIText

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.AlphaValues instead")
typealias AlphaValues = com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.AlphaValues

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.FontSizes instead")
typealias FontSizes = com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.FontSizes

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.BorderConstants instead")
typealias BorderConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.BorderConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.AnimationDurations instead")
typealias AnimationDurations = com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.AnimationDurations

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.AppConstants instead")
typealias AppConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.AppConstants

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.UIColors instead")
typealias UIColors = com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.UIColors

@Deprecated("Use com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.GraphicsConstants instead")
typealias GraphicsConstants = com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.GraphicsConstants
