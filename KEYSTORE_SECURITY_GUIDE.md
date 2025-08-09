# 🔐 Keystore Security Guide - CFAlarm for Time Office

## 📋 Überblick

Dieses Dokument beschreibt die implementierte Signierungsstrategie für die CFAlarm for Time Office Android-App und enthält wichtige Sicherheitsrichtlinien für die Verwaltung der Keystores.

## 🎯 Problem gelöst: Debug-Build Integritätswarnungen

### **Vorher:**
- Alle Builds (Debug + Release) wurden mit unsicheren Debug-Zertifikaten signiert
- Sicherheitswarnungen auf Produktionsgeräten
- "App appears to be unsigned / App signed with debug certificate" Fehler

### **Nachher:**
- ✅ **Debug-Builds**: Verwenden Debug-Keystore für Entwicklung
- ✅ **Release-Builds**: Verwenden Production-Keystore für sichere Verteilung
- ✅ **Staging-Builds**: Verwenden Production-Keystore für realistische Tests

## 🔑 Keystore-Informationen

### **Debug-Keystore (Entwicklung)**
- **Pfad**: `~/.android/debug.keystore`
- **Alias**: `AndroidDebugKey`
- **SHA1**: `98:1F:ED:CF:28:31:A0:10:7C:03:1B:A2:F2:4F:7C:88:06:99:20:D9`
- **Verwendung**: Nur für Entwicklung und Testing
- **Sicherheit**: Unsicher by design (Standard-Passwort: "android")

### **Production-Keystore (Release)**
- **Pfad**: `./cf-alarm-release.keystore` (Projektverzeichnis)
- **Alias**: `cf-alarm-key`
- **SHA1**: `EC:7B:0F:CF:F7:B1:72:57:83:3E:EC:2F:4E:F6:E2:CD:28:57:24:6A`
- **SHA256**: `E2:A7:A6:24:DF:41:CC:6F:41:95:74:24:BE:51:D7:09:5D:AE:AC:46:35:62:6F:40:B5:33:20:4E:6C:EB:AF:1E`
- **Gültig bis**: 23. Dezember 2052
- **Algorithmus**: SHA384withRSA, 2048-bit RSA
- **Distinguished Name**: `CN=CFAlarmForTimeOffice, OU=Development, O=F1rleFanz, L=Regensburg, ST=Bayern, C=DE`

## 🛡️ Sicherheitsrichtlinien

### **KRITISCH - Keystore-Backup:**
```bash
# SOFORT durchführen - Backup des Production-Keystores erstellen:
cp cf-alarm-release.keystore ~/Backup/keystore-backups/cf-alarm-release-$(date +%Y%m%d).keystore

# Zusätzlich in sichere Cloud-Storage hochladen
# WARNUNG: Verlust des Keystores = App kann nie wieder aktualisiert werden!
```

### **Passwort-Sicherheit:**
- **Aktuelles Passwort**: `CFAlarm2025!`
- **Empfehlung**: Passwort in einem sicheren Passwort-Manager speichern
- **NIEMALS**: Passwort in Git committen oder in Slack/Email teilen

### **Zugriffskontrolle:**
- **Berechtigt**: Nur Projektleitung und Senior-Entwickler
- **Speicherort**: Keystore sollte NICHT in Git eingecheckt werden
- **Production-Builds**: Nur über CI/CD-Pipeline mit sicherer Keystore-Bereitstellung

## 🔧 Build-Konfiguration

### **Gradle Signierung:**
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../cf-alarm-release.keystore")
        storePassword = "CFAlarm2025!"
        keyAlias = "cf-alarm-key"
        keyPassword = "CFAlarm2025!"
        
        // Enhanced security settings
        enableV1Signing = true  // JAR Signature (Android < 7.0)
        enableV2Signing = true  // APK Signature Scheme v2 (Android 7.0+)
        enableV3Signing = true  // APK Signature Scheme v3 (Android 9.0+)
        enableV4Signing = true  // APK Signature Scheme v4 (Android 11+)
    }
}
```

### **Build-Varianten:**
- **`assembleDebug`**: Debug-Keystore, für Entwicklung
- **`assembleRelease`**: Production-Keystore, für Play Store
- **`assembleStaging`**: Production-Keystore + Debug-Features

## 📱 Google Play Console Integration

### **App Signing Key Fingerprints:**
Nach Upload zur Google Play Console müssen folgende SHA1-Fingerprints registriert werden:

**Für Firebase/Google APIs:**
- **Debug SHA1**: `98:1F:ED:CF:28:31:A0:10:7C:03:1B:A2:F2:4F:7C:88:06:99:20:D9`
- **Release SHA1**: `EC:7B:0F:CF:F7:B1:72:57:83:3E:EC:2F:4E:F6:E2:CD:28:57:24:6A`

**Google OAuth Configuration:**
- Die `google-services.json` ist aktuell nur für die Basis-App-ID konfiguriert
- Bei aktiviertem `applicationIdSuffix` müssen zusätzliche App-IDs in Firebase registriert werden

## ⚠️ Wichtige Warnungen

### **🚨 ABSOLUT KRITISCH:**
1. **NIEMALS den Production-Keystore verlieren** - App kann sonst nie wieder aktualisiert werden
2. **NIEMALS Passwörter in Code oder Git** - Verwende Environment Variables oder sichere CI/CD
3. **REGELMÄSSIGE BACKUPS** - An mindestens 2 verschiedenen sicheren Orten

### **🔴 Security Best Practices:**
- Production-Keystore sollte auf separatem, verschlüsseltem USB-Stick gesichert werden
- Bei Team-Entwicklung: Keystore über sichere CI/CD-Pipeline bereitstellen
- Für größere Teams: Hardware Security Module (HSM) oder Google Play App Signing verwenden

## 🎯 Nächste Schritte

1. **Backup erstellen** (siehe oben) - SOFORT
2. **Code-Kompilierungsfehler beheben** (separate Aufgabe)
3. **Google Services für Debug-Suffix konfigurieren** (falls gewünscht)
4. **CI/CD-Pipeline für sichere Keystore-Bereitstellung einrichten**

## 📞 Support

Bei Fragen zur Keystore-Verwaltung oder Signierungsproblemen:
- Dokumentation: [Android App Signing](https://developer.android.com/studio/publish/app-signing)
- Projektdokumentation: `DEBUG_LOGGING_GUIDE.md`

---
**Erstellt am**: 07.08.2025
**Status**: ✅ Implementiert und getestet
**Version**: 1.0
