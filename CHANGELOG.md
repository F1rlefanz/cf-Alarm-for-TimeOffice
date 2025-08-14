# Changelog

Alle bemerkenswerten Änderungen an diesem Projekt werden in dieser Datei dokumentiert.

Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.0.0/),
und dieses Projekt verwendet [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Geplant
- Wear OS Support
- Spotify Integration für Weckmusik
- Home Screen Widgets
- Cloud Backup & Sync
- Statistiken über Schlafmuster

## [1.0.0] - 2025-01-20

### 🎉 Erste stabile Veröffentlichung

### Hinzugefügt
- **Google Calendar Integration**
  - OAuth 2.0 Authentifizierung
  - Automatische Kalendersynchronisation
  - Multi-Kalender Support
  - Ereignisfilterung nach Keywords

- **Philips Hue Integration**
  - Automatische Bridge-Erkennung
  - Sunrise Simulation (30 Min vor Alarm)
  - Individuelle Lampenauswahl
  - Farbtemperatur-Anpassung

- **Alarm-Management**
  - Intelligente Alarmplanung basierend auf Kalenderterminen
  - Manuelle Alarmerstellung
  - Wiederholende Alarme
  - Snooze-Funktion (5/10/15 Min)
  - Multiple Alarmtöne

- **Schichtarbeiter-Features**
  - Schichtmuster-Erkennung
  - Automatische Vorlaufzeit-Berechnung
  - Frühschicht/Spätschicht/Nachtschicht Support
  - Wochenend-Handling

- **Sicherheit & Datenschutz**
  - AES-256-GCM Verschlüsselung für OAuth-Tokens
  - Lokale Datenspeicherung
  - Keine Cloud-Abhängigkeiten
  - Open Source Code

- **Benutzeroberfläche**
  - Material Design 3
  - Dark Mode Support
  - Deutsche und englische Sprache
  - Intuitive Navigation
  - Onboarding-Tutorial

### Technische Details
- Kotlin 2.1.0
- Jetpack Compose UI
- MVVM Architektur
- Hilt Dependency Injection
- Coroutines für Async-Operationen
- WorkManager für Background-Tasks

## [0.9.0-beta] - 2024-12-15

### Hinzugefügt
- Beta-Version für Tester
- Basis-Alarmfunktionen
- Google Calendar Prototyp
- Erste UI-Implementation

### Geändert
- Migration zu Jetpack Compose
- Verbessertes Error Handling

### Behoben
- Memory Leaks in ViewModels
- Alarm-Zuverlässigkeit auf Android 14

## [0.5.0-alpha] - 2024-11-01

### Hinzugefügt
- Proof of Concept
- Grundlegende Alarmfunktionen
- Einfache Kalenderanbindung

### Bekannte Probleme
- Keine Persistenz nach Neustart
- UI noch nicht final
- Performance-Optimierung ausstehend

---

## Versionsschema

- **Major (X.0.0)**: Große, breaking changes oder komplett neue Features
- **Minor (0.X.0)**: Neue Features, die rückwärtskompatibel sind
- **Patch (0.0.X)**: Bugfixes und kleine Verbesserungen

## Links

- [GitHub Repository](https://github.com/f1rlefanz/cf-alarmfortimeoffice)
- [Issue Tracker](https://github.com/f1rlefanz/cf-alarmfortimeoffice/issues)
- [Releases](https://github.com/f1rlefanz/cf-alarmfortimeoffice/releases)