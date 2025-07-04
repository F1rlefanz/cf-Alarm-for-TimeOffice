package com.github.f1rlefanz.cf_alarmfortimeoffice.util

import androidx.compose.ui.unit.dp

/**
 * Zentrale Konstanten für die CF-Alarm App
 * Ersetzt hardcodierte Werte durch typisierte Konstanten
 */

// ============================
// CALENDAR & TIME CONSTANTS
// ============================
object CalendarConstants {
    /** Standard-Vorausschau für Kalender-Events in Tagen */
    const val DEFAULT_DAYS_AHEAD = 7
    
    /** Maximale Vorausschau für Kalender-Events in Tagen */
    const val MAX_DAYS_AHEAD = 30
    
    /** Maximale Anzahl von Events pro Kalendar-Abfrage */
    const val MAX_EVENTS_PER_QUERY = 50
    
    /** Token-Gültigkeitsdauer in Millisekunden (1 Stunde) */
    const val TOKEN_VALIDITY_MS = 3600000L
    
    /** Auto-Refresh Intervall für Kalender-Daten in Minuten */
    const val AUTO_REFRESH_INTERVAL_MINUTES = 15
    
    /** Alarm-Polling Intervall in Millisekunden */
    const val ALARM_POLLING_INTERVAL_MS = 5000L
    
    /** Standard Event-Dauer in Millisekunden (1 Stunde) */
    const val DEFAULT_EVENT_DURATION_MS = 3600000L
}

// ============================
// NETWORK & TIMEOUT CONSTANTS
// ============================
object NetworkConstants {
    /** Standard-Timeout für HTTP-Requests in Sekunden */
    const val HTTP_TIMEOUT_SECONDS = 30
    
    /** Timeout für Authentifizierung in Sekunden */
    const val AUTH_TIMEOUT_SECONDS = 60
    
    /** Retry-Anzahl für fehlgeschlagene Requests */
    const val MAX_RETRY_ATTEMPTS = 3
    
    /** Delay zwischen Retry-Versuchen in Millisekunden */
    const val RETRY_DELAY_MS = 1000L
}

// ============================
// UI TIMING CONSTANTS
// ============================
object UIConstants {
    /** Standard-Animation-Dauer in Millisekunden */
    const val ANIMATION_DURATION_MS = 300L
    
    /** Kurze Animation-Dauer in Millisekunden */
    const val ANIMATION_DURATION_SHORT_MS = 150L
    
    /** Lange Animation-Dauer für aufwändige Animationen in Millisekunden */
    const val ANIMATION_DURATION_LONG_MS = 3000L
    
    /** Debounce-Delay für Benutzereingaben in Millisekunden */
    const val INPUT_DEBOUNCE_MS = 300L
    
    /** Auto-Dismiss Zeit für Snackbars in Millisekunden */
    const val SNACKBAR_AUTO_DISMISS_MS = 4000L
    
    /** Auto-Dismiss Zeit für Error Messages in Millisekunden */
    const val ERROR_MESSAGE_AUTO_DISMISS_MS = 5000L
    
    /** Delay für UI-Stabilisierung nach State-Änderungen */
    const val UI_STABILITY_DELAY_MS = 300L
    
    /** Vibrations-Pattern für Alarme */
    val ALARM_VIBRATION_PATTERN = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500)
}

// ============================
// SPACING & DIMENSION CONSTANTS
// ============================
object SpacingConstants {
    // Micro-Abstände für sehr kleine UI-Elemente
    val SPACING_MICRO = 2.dp
    val SPACING_TINY = 6.dp
    
    // Standard-Abstände
    val SPACING_EXTRA_SMALL = 4.dp
    val SPACING_SMALL = 8.dp
    val SPACING_MEDIUM = 12.dp
    val SPACING_LARGE = 16.dp
    val SPACING_EXTRA_LARGE = 24.dp
    val SPACING_XXL = 32.dp
    val SPACING_XXXL = 48.dp
    
    // Padding-Konstanten
    val PADDING_SCREEN_HORIZONTAL = 16.dp
    val PADDING_SCREEN_VERTICAL = 16.dp
    val PADDING_CARD = 16.dp
    val PADDING_SMALL = 8.dp
    
    // Icon-Größen
    val ICON_SIZE_SMALL = 16.dp
    val ICON_SIZE_MEDIUM = 18.dp
    val ICON_SIZE_STANDARD = 20.dp
    val ICON_SIZE_LARGE = 24.dp
    val ICON_SIZE_EXTRA_LARGE = 32.dp
    val ICON_SIZE_XXL = 48.dp
    val ICON_SIZE_XXXL = 64.dp
    val ICON_SIZE_GIANT = 80.dp
    
    // Spezielle UI-Elemente
    val APP_ICON_SIZE = 120.dp
    val FULLSCREEN_ELEMENT_SIZE = 140.dp
    
    // Button-Dimensionen
    val BUTTON_HEIGHT_STANDARD = 48.dp
    val BUTTON_HEIGHT_LARGE = 56.dp
    val BUTTON_HEIGHT_FULLSCREEN = 80.dp
    val BUTTON_MIN_WIDTH = 64.dp
    
    // Card & Surface
    val CARD_ELEVATION = 4.dp
    val SURFACE_CORNER_RADIUS = 8.dp
    val CARD_CORNER_RADIUS = 12.dp
    val FULLSCREEN_CORNER_RADIUS = 20.dp
}

// ============================
// LAYOUT FRACTION CONSTANTS
// ============================
object LayoutFractions {
    /** Breite für Dialoge (90% der Bildschirmbreite) */
    const val DIALOG_WIDTH = 0.9f
    
    /** Höhe für Dialoge (80% der Bildschirmhöhe) */  
    const val DIALOG_HEIGHT = 0.8f
    
    /** Standardbreite für Cards */
    const val CARD_WIDTH = 0.85f
    
    /** Vollbreite für wichtige Elemente */
    const val FULL_WIDTH = 1.0f
    
    /** Halbe Breite für zweispaltige Layouts */
    const val HALF_WIDTH = 0.5f
}

// ============================
// ALARM CONSTANTS
// ============================
object AlarmConstants {
    /** Standard-Vorlaufzeit für Alarme in Minuten vor Schichtbeginn */
    const val DEFAULT_ALARM_LEAD_TIME_MINUTES = 30
    
    /** Minimale Vorlaufzeit für Alarme in Minuten */
    const val MIN_ALARM_LEAD_TIME_MINUTES = 5
    
    /** Maximale Vorlaufzeit für Alarme in Minuten */
    const val MAX_ALARM_LEAD_TIME_MINUTES = 180
    
    /** Standard-Snooze-Zeit in Minuten */
    const val DEFAULT_SNOOZE_MINUTES = 5
    
    /** Maximale Anzahl von Snooze-Wiederholungen */
    const val MAX_SNOOZE_COUNT = 3
    
    /** Standard-Alarmzeit (Stunde) für Fallback-Fälle */
    const val DEFAULT_ALARM_HOUR = 6
    
    /** Standard-Alarmzeit (Minute) für Fallback-Fälle */
    const val DEFAULT_ALARM_MINUTE = 0
}

// ============================
// SHIFT RECOGNITION CONSTANTS
// ============================
object ShiftConstants {
    /** Minimale Schichtdauer in Stunden */
    const val MIN_SHIFT_DURATION_HOURS = 2
    
    /** Maximale Schichtdauer in Stunden */
    const val MAX_SHIFT_DURATION_HOURS = 16
    
    /** Standard-Toleranz für Schichterkennung in Minuten */
    const val SHIFT_RECOGNITION_TOLERANCE_MINUTES = 15
    
    /** Mindestabstand zwischen Schichten in Stunden */
    const val MIN_BREAK_BETWEEN_SHIFTS_HOURS = 8
}

// ============================
// DATA STORAGE CONSTANTS
// ============================
object StorageConstants {
    /** DataStore-Namen */
    const val AUTH_DATASTORE_NAME = "auth_prefs"
    const val SHIFT_DATASTORE_NAME = "shift_prefs"
    
    /** Maximale Anzahl von gespeicherten Alarm-Einträgen */
    const val MAX_STORED_ALARMS = 50
    
    /** Cache-Gültigkeitsdauer in Millisekunden */
    const val CACHE_VALIDITY_MS = 300000L // 5 Minuten
}

// ============================
// DATE & TIME FORMAT CONSTANTS
// ============================
object DateTimeFormats {
    /** Standard-Datum-Zeit-Format für UI-Anzeige */
    const val STANDARD_DATETIME = "dd.MM.yyyy HH:mm"
    
    /** Kurzes Datum-Format */
    const val SHORT_DATE = "dd.MM.yyyy"
    
    /** Zeit-Format für Uhrzeiten */
    const val TIME_ONLY = "HH:mm"
    
    /** Ausführliches Datum-Zeit-Format */
    const val VERBOSE_DATETIME = "EEEE, dd. MMMM yyyy HH:mm"
}

// ============================
// UI TEXT CONSTANTS
// ============================
object UIText {
    // Allgemeine Texte
    const val OVERVIEW = "Übersicht"
    const val REFRESH = "Aktualisieren"
    const val LOADING = "Lädt..."
    const val ERROR = "Fehler"
    const val WARNING = "Warnung"
    const val SUCCESS = "Erfolg"
    const val SAVE = "Speichern"
    const val CANCEL = "Abbrechen"
    const val DELETE = "Löschen"
    const val EDIT = "Bearbeiten"
    const val ADD = "Hinzufügen"
    const val BACK = "Zurück"
    const val OK = "OK"
    const val SELECTED = "Ausgewählt"
    
    // App-spezifische Texte
    const val APP_ICON_LETTERS = "CF"
    const val APP_TITLE = "CF-Alarm for TimeOffice"
    const val APP_SUBTITLE = "Automatische Alarmverwaltung für Ihre Schichten"
    const val GOOGLE_SIGN_IN = "Mit Google anmelden"
    const val PERMISSION_EXPLANATION = "Diese App benötigt Zugriff auf Ihren Google Kalender, " +
            "um Schichten zu erkennen und Alarme zu setzen."
    
    // Status-bezogene Texte
    const val SYSTEM_STATUS = "System Status"
    const val AUTHENTICATION = "Authentifizierung"
    const val LOGGED_IN_AS = "Angemeldet als:"
    const val UNKNOWN = "Unbekannt"
    const val NOT_LOGGED_IN = "Nicht angemeldet"
    const val NO_CALENDAR_SELECTED_STATUS = "Kein Kalender ausgewählt"
    const val NO_CALENDARS_AVAILABLE_STATUS = "Keine Kalender verfügbar"
    const val CALENDARS_SELECTED = "Kalender ausgewählt"
    const val SHIFT_TYPES_DEFINED = "Schichttypen definiert"
    const val NO_CONFIGURATION_AVAILABLE = "Keine Konfiguration vorhanden"
    const val SHIFT_RECOGNITION = "Schicht-Erkennung"
    const val NO_SHIFTS_RECOGNIZED = "Keine Schichten erkannt"
    const val ALARMS = "Alarme"
    const val ALARMS_SET = "Alarme gesetzt"
    const val DEBUG_INFORMATION = "Debug-Informationen"
    const val EVENTS_LOADED = "Events geladen"
    const val NO_SHIFT_SHORT = "Keine"
    const val NEXT_ALARM = "Nächster"
    const val CALENDAR = "Kalender"
    
    // Alarm-bezogene Texte
    const val ALARM_CONTROL = "Alarm-Steuerung"
    const val NEXT_SHIFT = "Nächste Schicht:"
    const val ALARM_STATUS = "Alarm Status"
    const val SET_ALARM = "Alarm setzen"
    const val CANCEL_ALARM = "Alarm löschen"
    const val ACTIVE_ALARMS = "aktive Alarme"
    const val NO_ACTIVE_ALARMS = "Keine aktiven Alarme"
    const val EXACT_ALARM_DISABLED = "Exakte Alarme sind in den Einstellungen deaktiviert"
    const val TIME_UNTIL_ALARM = "Zeit bis zum Wecker"
    const val TIME_EXPIRED = "Zeit abgelaufen!"
    const val ALARM_ACTIVE = "ALARM AKTIV!"
    const val AUTO_ALARMS = "Automatische Alarme"
    const val AUTO_ALARMS_DESCRIPTION = "Alarme automatisch für erkannte Schichten setzen"
    
    // Zeit-Einheiten
    const val UNIT_DAYS = "Tage"
    const val UNIT_HOURS = "Std"
    const val UNIT_MINUTES = "Min"
    const val UNIT_SECONDS = "Sek"
    
    // Schicht-bezogene Texte
    const val NEXT_SHIFT_LABEL = "Nächste Schicht"
    const val NO_SHIFT_DETECTED = "Keine Schicht erkannt"
    const val SHIFTS_RECOGNIZED = "als Schichten erkannt"
    const val SHIFT_CONFIGURATION = "Schicht-Konfiguration"
    const val ADD_SHIFT = "Schicht hinzufügen"
    const val SHIFT_TYPES = "Schichttypen"
    const val NO_SHIFT_TYPES_DEFINED = "Keine Schichttypen definiert"
    const val ADD_SHIFT_TYPES_HINT = "Füge Schichttypen hinzu, um die automatische Erkennung zu aktivieren"
    const val RESET_TO_DEFAULTS = "Auf Standardwerte zurücksetzen"
    const val PATTERN_LABEL = "Muster:"
    const val ALARM_LABEL = "Alarm:"
    
    // Kalender-bezogene Texte
    const val CALENDAR_EVENTS = "Kalender-Events"
    const val NO_EVENTS_LOADED = "Keine Events geladen"
    const val EVENTS_IN_NEXT_DAYS = "Events in den nächsten"
    const val DAYS_LABEL = "Tagen"
    const val SELECT_CALENDARS = "Kalender auswählen"
    const val SELECT_CALENDARS_INSTRUCTION = "Wähle einen oder mehrere Kalender aus, die für die Schichterkennung verwendet werden sollen."
    const val NO_CALENDARS_AVAILABLE = "Keine Kalender verfügbar. Stelle sicher, dass die Kalenderberechtigung erteilt wurde."
    
    // Dialog-Texte
    const val EDIT_SHIFT = "Schicht bearbeiten"
    const val ADD_SHIFT_DIALOG = "Schicht hinzufügen"
    const val DIALOG_IMPLEMENTATION_PENDING = "Dialog-Implementierung folgt..."
}

// ============================
// ALPHA & TRANSPARENCY CONSTANTS
// ============================
object AlphaValues {
    /** Sehr transparente Overlays */
    const val VERY_LIGHT = 0.05f
    
    /** Leichte Transparenz */
    const val LIGHT = 0.1f
    
    /** Mittlere Transparenz */
    const val MEDIUM = 0.15f
    
    /** Stärkere Transparenz */
    const val STRONG = 0.3f
    
    /** Für Disabled-States */
    const val DISABLED = 0.38f
    
    /** Für Surface-Variants */
    const val SURFACE_VARIANT = 0.5f
}

// ============================
// FONT SIZE CONSTANTS
// ============================
object FontSizes {
    /** Kleine Schriftgröße für Details */
    const val SMALL = 12
    
    /** Standard-Schriftgröße */
    const val MEDIUM = 14
    
    /** Große Schriftgröße für wichtige Elemente */
    const val LARGE = 18
    
    /** Extra große Schriftgröße */
    const val EXTRA_LARGE = 24
    
    /** Countdown-Timer große Zahlen */
    const val COUNTDOWN_LARGE = 28
    
    /** Countdown-Timer normale Zahlen */
    const val COUNTDOWN_NORMAL = 24
}

// ============================
// BORDER & STROKE CONSTANTS
// ============================
object BorderConstants {
    /** Standard-Border-Breite */
    const val STANDARD_WIDTH = 1
    
    /** Hervorgehobene Border-Breite */
    const val HIGHLIGHTED_WIDTH = 2
    
    /** Dicke Border für spezielle Fälle */
    const val THICK_WIDTH = 3
}

// ============================
// ANIMATION DURATION CONSTANTS
// ============================
object AnimationDurations {
    /** Timer-Update-Intervall in Millisekunden */
    const val TIMER_UPDATE_MS = 1000L
    
    /** Pulsieren-Animation für Timer */
    const val PULSE_MS = 1000L
    
    /** Blinken für kritische Zustände */
    const val BLINK_MS = 500L
    
    /** Schnelle UI-Übergänge */
    const val QUICK_MS = 150L
    
    /** Standard UI-Animationen */
    const val STANDARD_MS = 300L
    
    /** Lange Animationen */
    const val LONG_MS = 600L
}

// ============================
// APP METADATA CONSTANTS
// ============================
object AppConstants {
    const val APP_NAME = "CF-Alarm for TimeOffice"
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 1
    
    /** Google Web Client ID für OAuth */
    const val GOOGLE_WEB_CLIENT_ID = "931091152160-8s3nd7os2p61ac6ecm799gjhekkf0b4i.apps.googleusercontent.com"
}

// ============================
// UI COLOR CONSTANTS
// ============================
object UIColors {
    // Standard semantic colors for status indicators
    const val STATUS_SUCCESS = 0xFF4CAF50L // Green
    const val STATUS_WARNING = 0xFFFF9800L // Orange
    const val STATUS_INFO = 0xFF2196F3L    // Blue
    const val STATUS_ERROR = 0xFFF44336L   // Red
    const val STATUS_LOADING = 0xFF9C27B0L // Purple
    
    // Warning and info container colors for light theme
    const val WARNING_CONTAINER_LIGHT = 0xFFFFF3E0L // Light orange
    const val ON_WARNING_CONTAINER_LIGHT = 0xFF5D4037L // Dark brown
    const val WARNING_CONTAINER_DARK = 0xFF3E2723L // Dark brown
    const val ON_WARNING_CONTAINER_DARK = 0xFFFFCC80L // Light orange
    const val WARNING_COLOR = 0xFFFF9800L // Orange
    const val ON_WARNING_COLOR = 0xFFFFFFFFL // White
}

// ============================
// UI GRAPHICS CONSTANTS
// ============================
object GraphicsConstants {
    /** Standard radius for gradient effects */
    const val GRADIENT_RADIUS = 500f
    
    /** Standard corner radius for UI elements */
    const val STANDARD_CORNER_RADIUS = 8f
    
    /** Large corner radius for prominent elements */
    const val LARGE_CORNER_RADIUS = 12f
    
    /** Extra large corner radius for special cases */
    const val EXTRA_LARGE_CORNER_RADIUS = 20f
}
