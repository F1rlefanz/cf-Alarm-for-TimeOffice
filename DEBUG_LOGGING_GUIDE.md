# Debug Logging Guide

## 🎯 Zweck
Sammelt persistente Logs für 3-Tage-Analyse, die auch nach App-Neustarts und Geräte-Neustarts erhalten bleiben.

## 📁 Log-Dateien
- **Hauptdatei**: `/sdcard/Android/data/com.github.f1rlefanz.cf_alarmfortimeoffice/files/debug_logs.txt`
- **Backup-Datei**: `/sdcard/Android/data/com.github.f1rlefanz.cf_alarmfortimeoffice/files/debug_logs_backup.txt`

## 🔧 Automatische Features
- **Rotation**: Log-Datei wird automatisch bei 40MB rotiert
- **Maximalgröße**: 50MB pro Datei
- **Format**: `MM-dd HH:mm:ss.SSS LEVEL/TAG: Message`

## 📲 Log-Datei vom Handy holen

### Option 1: ADB (Empfohlen)
```bash
# Hauptdatei holen
adb pull /sdcard/Android/data/com.github.f1rlefanz.cf_alarmfortimeoffice/files/debug_logs.txt ./

# Backup-Datei holen (falls vorhanden)
adb pull /sdcard/Android/data/com.github.f1rlefanz.cf_alarmfortimeoffice/files/debug_logs_backup.txt ./
```

### Option 2: Android Studio Device Explorer
1. View → Tool Windows → Device Explorer
2. Navigiere zu: `data/data/com.github.f1rlefanz.cf_alarmfortimeoffice/files/`
3. Rechtsklick auf `debug_logs.txt` → Save As

### Option 3: File Manager App
1. Öffne einen File Manager (z.B. Files by Google)
2. Navigiere zu: `Android/data/com.github.f1rlefanz.cf_alarmfortimeoffice/files/`
3. Teile die `debug_logs.txt` via E-Mail/Cloud

## 📊 Log-Analyse
- **Öffne mit**: Notepad++, Visual Studio Code, oder einem anderen Text-Editor
- **Grep-Suche**: `grep "ERROR" debug_logs.txt` für nur Fehler
- **Zeitbereich**: Logs haben Timestamp im Format `MM-dd HH:mm:ss.SSS`

## 🧪 Testing
Um zu testen, ob das Logging funktioniert, kannst du in der App folgende Funktion aufrufen:

```kotlin
// In einer Activity oder Fragment
DebugLogInfo.addTestLogEntries()
DebugLogInfo.logFileInfo(this)
```

## 🔍 Erwartete Log-Größe für 3 Tage
- **Normal**: ~10-20 MB
- **Intensiv**: ~40-50 MB
- **Bei Problemen**: Kann größer werden (automatische Rotation)

## 🚀 Deployment
- Funktioniert nur in **DEBUG** builds
- **Release** builds haben kein File-Logging (nur Crashlytics)
- Keine Performance-Auswirkungen im Release-Build
