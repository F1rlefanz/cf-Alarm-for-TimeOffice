# Contributing to CF Alarm for Time Office

Vielen Dank für dein Interesse, zu CF Alarm for Time Office beizutragen! 🎉

## 📋 Inhaltsverzeichnis

- [Code of Conduct](#code-of-conduct)
- [Wie kann ich beitragen?](#wie-kann-ich-beitragen)
- [Entwicklungsumgebung einrichten](#entwicklungsumgebung-einrichten)
- [Entwicklungsprozess](#entwicklungsprozess)
- [Coding Standards](#coding-standards)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Prozess](#pull-request-prozess)

## Code of Conduct

Dieses Projekt und alle Teilnehmer verpflichten sich zu einem offenen und einladenden Umfeld. Wir erwarten von allen Beitragenden:

- Respektvolle und konstruktive Kommunikation
- Konstruktives Feedback geben und annehmen
- Fokus auf das Beste für die Community
- Empathie gegenüber anderen Community-Mitgliedern

## Wie kann ich beitragen?

### 🐛 Bugs melden

Bevor du einen Bug meldest:
1. Überprüfe die [bestehenden Issues](https://github.com/f1rlefanz/cf-alarmfortimeoffice/issues)
2. Stelle sicher, dass du die neueste Version verwendest
3. Sammle relevante Informationen (Android-Version, Gerät, Schritte zur Reproduktion)

Bug-Report erstellen:
```markdown
**Beschreibung**
Eine klare Beschreibung des Problems.

**Schritte zur Reproduktion**
1. Gehe zu '...'
2. Klicke auf '....'
3. Scrolle zu '....'
4. Fehler tritt auf

**Erwartetes Verhalten**
Was sollte passieren?

**Screenshots**
Falls relevant, füge Screenshots hinzu.

**Umgebung**
- Gerät: [z.B. OnePlus 9]
- Android Version: [z.B. Android 14]
- App Version: [z.B. 1.0.0]
```

### 💡 Features vorschlagen

Feature Requests sind willkommen! Bitte erstelle ein Issue mit dem Label "enhancement" und beschreibe:
- Das Problem, das das Feature löst
- Mögliche Implementierungsansätze
- Mockups oder Beispiele (falls vorhanden)

### 🔧 Code beitragen

1. Forke das Repository
2. Erstelle einen Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Committe deine Änderungen (siehe [Commit Guidelines](#commit-guidelines))
4. Push zum Branch (`git push origin feature/AmazingFeature`)
5. Öffne einen Pull Request

## Entwicklungsumgebung einrichten

### Voraussetzungen

- **Android Studio**: Narwhal (2025.1.1) oder neuer
- **JDK**: Version 17
- **Android SDK**: API Level 36
- **Kotlin**: Version 2.1.0
- **Git**: Für Versionskontrolle

### Setup

```bash
# Repository klonen
git clone https://github.com/f1rlefanz/cf-alarmfortimeoffice.git
cd cf-alarmfortimeoffice

# In Android Studio öffnen
# File -> Open -> Wähle den Projektordner

# Gradle Sync durchführen
# Build -> Make Project

# Tests ausführen
./gradlew test

# App auf Emulator/Gerät starten
# Run -> Run 'app'
```

### Konfiguration für Entwicklung

1. **Google Calendar API**:
   - Erstelle ein Projekt in der [Google Cloud Console](https://console.cloud.google.com)
   - Aktiviere die Calendar API
   - Erstelle OAuth 2.0 Credentials
   - Füge die `google-services.json` zu `app/` hinzu

2. **Firebase (Optional für Crashlytics)**:
   - Projekt in [Firebase Console](https://console.firebase.google.com) erstellen
   - Crashlytics aktivieren
   - `google-services.json` aktualisieren

## Entwicklungsprozess

### Branching Strategie

- `main` - Stabiler, production-ready Code
- `develop` - Aktuelle Entwicklung
- `feature/*` - Neue Features
- `bugfix/*` - Bugfixes
- `hotfix/*` - Kritische Fixes für Production

### Workflow

1. Erstelle einen Issue für deine Änderung
2. Forke das Repository
3. Erstelle einen Branch vom `develop` Branch
4. Implementiere deine Änderungen
5. Schreibe/aktualisiere Tests
6. Stelle sicher, dass alle Tests bestehen
7. Erstelle einen Pull Request gegen `develop`

## Coding Standards

### Kotlin Style Guide

Wir folgen den [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) mit folgenden Ergänzungen:

```kotlin
// ✅ Gut: Aussagekräftige Namen
class AlarmSchedulerImpl : AlarmScheduler {
    fun scheduleAlarmForCalendarEvent(event: CalendarEvent): AlarmId
}

// ❌ Schlecht: Generische Namen
class Manager {
    fun process(data: Any): String
}
```

### Architektur-Prinzipien

- **MVVM Pattern**: ViewModels für UI-Logik
- **Repository Pattern**: Datenzugriff abstrahieren
- **Use Cases**: Business-Logik kapseln
- **Dependency Injection**: Hilt für DI verwenden

### Code-Organisation

```
com.github.f1rlefanz.cf_alarmfortimeoffice/
├── alarm/          # Alarm-bezogene Features
├── auth/           # Authentifizierung
├── calendar/       # Kalender-Integration
├── data/           # Datenmodelle
├── di/             # Dependency Injection Module
├── hue/            # Philips Hue Integration
├── repository/     # Repository Implementierungen
├── ui/             # Compose UI Komponenten
├── usecase/        # Business Logic
├── util/           # Hilfsfunktionen
└── viewmodel/      # ViewModels
```

### Best Practices

1. **Fehlerbehandlung**
```kotlin
// Verwende Result<T> für Operationen, die fehlschlagen können
suspend fun fetchCalendarEvents(): Result<List<CalendarEvent>> {
    return try {
        val events = calendarApi.getEvents()
        Result.success(events)
    } catch (e: Exception) {
        Timber.e(e, "Failed to fetch calendar events")
        Result.failure(e)
    }
}
```

2. **Coroutines & Flow**
```kotlin
// Verwende Flow für reaktive Daten
val alarmState: StateFlow<AlarmState> = repository
    .observeAlarms()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AlarmState.Loading
    )
```

3. **Compose UI**
```kotlin
// Halte Composables klein und fokussiert
@Composable
fun AlarmCard(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Implementation
}
```

## Commit Guidelines

Wir verwenden [Conventional Commits](https://www.conventionalcommits.org/):

### Format
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
- `feat`: Neues Feature
- `fix`: Bugfix
- `docs`: Dokumentationsänderungen
- `style`: Code-Formatierung (keine Funktionsänderung)
- `refactor`: Code-Refactoring
- `perf`: Performance-Verbesserungen
- `test`: Tests hinzufügen/ändern
- `build`: Build-System oder Dependencies
- `ci`: CI/CD Änderungen
- `chore`: Sonstige Änderungen

### Beispiele
```bash
# Feature
feat(alarm): Add sunrise simulation for Hue lights

# Bugfix
fix(calendar): Resolve timezone issues in event parsing

# Dokumentation
docs(readme): Update installation instructions

# Refactoring
refactor(viewmodel): Extract alarm logic to use case
```

## Pull Request Prozess

### Bevor du einen PR erstellst

- [ ] Code folgt den Coding Standards
- [ ] Alle Tests bestehen (`./gradlew test`)
- [ ] Lint-Checks bestehen (`./gradlew lint`)
- [ ] Dokumentation ist aktualisiert
- [ ] CHANGELOG.md ist aktualisiert (für Features/Fixes)

### PR Template

```markdown
## Beschreibung
Kurze Beschreibung der Änderungen.

## Art der Änderung
- [ ] Bugfix
- [ ] Neues Feature
- [ ] Breaking Change
- [ ] Dokumentation

## Checklist
- [ ] Code folgt den Style Guidelines
- [ ] Selbst-Review durchgeführt
- [ ] Tests hinzugefügt/aktualisiert
- [ ] Dokumentation aktualisiert

## Screenshots (falls UI-Änderungen)
Vorher/Nachher Screenshots

## Verwandte Issues
Fixes #123
```

### Review-Prozess

1. Mindestens ein Review erforderlich
2. Alle CI-Checks müssen bestehen
3. Keine Konflikte mit Target-Branch
4. Squash & Merge für saubere Historie

## Testing

### Unit Tests
```kotlin
@Test
fun `schedule alarm sets correct time`() {
    // Given
    val event = CalendarEvent(
        title = "Work",
        startTime = LocalDateTime.of(2025, 1, 20, 9, 0)
    )
    
    // When
    val alarmTime = scheduler.calculateAlarmTime(event, leadTime = 30)
    
    // Then
    assertEquals(
        LocalDateTime.of(2025, 1, 20, 8, 30),
        alarmTime
    )
}
```

### UI Tests
```kotlin
@Test
fun alarmToggle_changesState() {
    composeTestRule.setContent {
        AlarmScreen()
    }
    
    composeTestRule
        .onNodeWithTag("alarm_toggle")
        .performClick()
    
    composeTestRule
        .onNodeWithText("Alarm activated")
        .assertIsDisplayed()
}
```

## Ressourcen

- [Android Developer Docs](https://developer.android.com)
- [Kotlin Documentation](https://kotlinlang.org/docs)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io)

## Fragen?

Bei Fragen kannst du:
- Ein [Issue](https://github.com/f1rlefanz/cf-alarmfortimeoffice/issues) erstellen
- Eine [Discussion](https://github.com/f1rlefanz/cf-alarmfortimeoffice/discussions) starten
- Den Maintainer kontaktieren

---

Vielen Dank für deinen Beitrag! 🚀 Issue erstellen oder auswählen
2. Branch von `develop` erstellen
3. Änderungen implementieren
4. Tests schreiben/aktualisieren
5. Lokale Tests durchführen
6. Commit und Push
7. Pull Request erstellen
8. Code Review abwarten
9. Nach Approval: Merge in `develop`

## Coding Standards

### Kotlin Style Guide

Wir folgen den [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) mit folgenden Zusätzen:

```kotlin
// Paket-Struktur
package com.github.f1rlefanz.cf_alarmfortimeoffice.feature

// Import-Reihenfolge
import android.*           // Android imports
import androidx.*          // AndroidX imports
import java.*             // Java imports
import javax.*            // JavaX imports
import kotlin.*           // Kotlin imports
import kotlinx.*          // KotlinX imports
import com.github.*       // App imports
import com.google.*       // External library imports

// Klassen-Struktur
class ExampleViewModel @Inject constructor(
    private val useCase: ExampleUseCase
) : ViewModel() {
    
    // Constants
    companion object {
        private const val TIMEOUT_MS = 5000L
    }
    
    // State
    private val _uiState = MutableStateFlow(ExampleUiState())
    val uiState: StateFlow<ExampleUiState> = _uiState.asStateFlow()
    
    // Public functions
    fun performAction() {
        viewModelScope.launch {
            // Implementation
        }
    }
    
    // Private functions
    private fun updateState(newState: ExampleUiState) {
        _uiState.value = newState
    }
}
```

### Architektur-Prinzipien

- **MVVM Pattern**: ViewModels für UI-Logik
- **Repository Pattern**: Abstraktion der Datenquellen
- **Use Cases**: Business-Logik Kapselung
- **Dependency Injection**: Hilt für DI
- **Single Source of Truth**: StateFlow für UI-State

### Testing

Jeder neue Code sollte Tests beinhalten:

```kotlin
// Unit Test Beispiel
@Test
fun `when alarm is set then notification is scheduled`() {
    // Given
    val alarm = Alarm(time = LocalTime.of(7, 0))
    
    // When
    alarmManager.setAlarm(alarm)
    
    // Then
    verify(notificationScheduler).schedule(alarm)
}
```

Testabdeckung Ziele:
- Unit Tests: >80%
- UI Tests: Kritische User Flows
- Integration Tests: API-Kommunikation

## Commit Guidelines

Wir verwenden [Conventional Commits](https://www.conventionalcommits.org/):

### Format
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
- `feat`: Neues Feature
- `fix`: Bugfix
- `docs`: Dokumentationsänderungen
- `style`: Code-Formatierung
- `refactor`: Code-Refactoring
- `test`: Tests hinzufügen/ändern
- `chore`: Build-Prozess, Dependencies
- `perf`: Performance-Verbesserungen

### Beispiele
```bash
feat(alarm): add snooze duration customization

Allow users to set custom snooze durations
between 1 and 30 minutes.

Closes #123
```

```bash
fix(calendar): resolve sync issue with all-day events

All-day events were being ignored during sync.
Added proper handling for events without specific times.

Fixes #456
```

## Pull Request Prozess

### Vor dem PR

- [ ] Code folgt den Style Guidelines
- [ ] Alle Tests laufen erfolgreich (`./gradlew test`)
- [ ] Lint-Checks bestanden (`./gradlew lint`)
- [ ] Code ist selbst-dokumentierend oder kommentiert
- [ ] CHANGELOG.md wurde aktualisiert (bei Features/Breaking Changes)

### PR Template

```markdown
## Beschreibung
Kurze Beschreibung der Änderungen

## Art der Änderung
- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update

## Checklist
- [ ] Mein Code folgt den Style Guidelines
- [ ] Ich habe selbst Review durchgeführt
- [ ] Ich habe Kommentare hinzugefügt (wo nötig)
- [ ] Ich habe die Dokumentation aktualisiert
- [ ] Meine Änderungen generieren keine Warnings
- [ ] Ich habe Tests hinzugefügt
- [ ] Alle Tests laufen erfolgreich

## Screenshots (falls UI-Änderungen)
Vorher | Nachher

## Related Issues
Closes #(issue)
```

### Review-Prozess

1. **Automatische Checks**: GitHub Actions führt Tests aus
2. **Code Review**: Mindestens 1 Approval erforderlich
3. **Testing**: Manuelles Testing auf Gerät/Emulator
4. **Merge**: Squash and Merge in `develop`

### Nach dem Merge

- Branch kann gelöscht werden
- Issue wird automatisch geschlossen (wenn verlinkt)
- Änderungen werden im nächsten Release enthalten sein

## 🎯 Prioritäten für Contributions

Aktuelle Fokusgebiete wo Hilfe besonders willkommen ist:

1. **Testing**: Unit Tests und UI Tests
2. **Dokumentation**: Übersetzungen, Tutorials
3. **Performance**: Optimierungen für Battery Life
4. **Features**: Wear OS Support, Widget Development
5. **Accessibility**: Screen Reader Support, Kontrast-Verbesserungen

## 📬 Kontakt

- **Issues**: [GitHub Issues](https://github.com/f1rlefanz/cf-alarmfortimeoffice/issues)
- **Discussions**: [GitHub Discussions](https://github.com/f1rlefanz/cf-alarmfortimeoffice/discussions)
- **E-Mail**: [Entwickler kontaktieren]

## 🙏 Danke!

Vielen Dank, dass du dir die Zeit nimmst, zu diesem Projekt beizutragen! Jeder Beitrag, egal wie klein, wird geschätzt und hilft, die App für alle Nutzer zu verbessern.

Happy Coding! 🚀