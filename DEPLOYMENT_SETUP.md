# 🚀 CI/CD Deployment Setup Guide

## 📋 GitHub Secrets Configuration

Für die sichere CI/CD-Pipeline müssen folgende Secrets in GitHub konfiguriert werden:

### **🔐 Keystore Secrets:**

1. **KEYSTORE_BASE64**
   ```bash
   # Konvertiere den Keystore zu Base64:
   base64 -i cf-alarm-release.keystore -o keystore.base64
   # Kopiere den Inhalt von keystore.base64 in GitHub Secret
   ```

2. **KEYSTORE_PASSWORD**
   ```
   Value: CFAlarm2025!
   ```

3. **KEY_ALIAS**
   ```
   Value: cf-alarm-key
   ```

4. **KEY_PASSWORD**
   ```
   Value: CFAlarm2025!
   ```

### **🚀 Google Play Secrets (Optional):**

5. **GOOGLE_PLAY_SERVICE_ACCOUNT**
   ```json
   {
     "type": "service_account",
     "project_id": "your-project",
     "private_key_id": "...",
     "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
     "client_email": "...",
     "client_id": "...",
     "auth_uri": "https://accounts.google.com/o/oauth2/auth",
     "token_uri": "https://oauth2.googleapis.com/token"
   }
   ```

## 🛠️ Setup Anleitung:

### **Schritt 1: GitHub Repository Secrets**
```bash
# Navigate to: GitHub Repo → Settings → Secrets and variables → Actions
# Add New Repository Secret für jeden der oben genannten Werte
```

### **Schritt 2: Keystore Base64 Erstellen**
```bash
# Powershell:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("cf-alarm-release.keystore")) | Out-File keystore.base64

# Linux/Mac:
base64 -i cf-alarm-release.keystore > keystore.base64
```

### **Schritt 3: Pipeline Testen**
```bash
# Push einen Tag um Release-Build zu triggern:
git tag v1.0.0
git push origin v1.0.0

# Oder manuell triggern über GitHub Actions UI
```

## 🔒 Sicherheitsrichtlinien:

- **NIEMALS** Keystore-Dateien in das Repository committen
- **NUR** Base64-kodierte Keystores in GitHub Secrets
- **REGELMÄSSIG** Secrets rotieren (alle 6-12 Monate)
- **TEAM-ZUGRIFF** auf Repository-Secrets beschränken

## 📱 Google Play Console Setup:

1. **Service Account erstellen** in Google Cloud Console
2. **Play Console API** aktivieren
3. **Berechtigungen** für App-Release vergeben
4. **JSON-Key** herunterladen und als Secret speichern

## 🎯 Release-Workflow:

```bash
# Lokale Entwicklung:
git commit -am "Release v1.0.1"
git tag v1.0.1
git push origin main
git push origin v1.0.1

# Automatisch:
# → GitHub Actions builds signed APK/AAB
# → Artifacts werden hochgeladen
# → Optional: Auto-deployment zu Play Store Beta
```

## 🆘 Troubleshooting:

**Problem: "Keystore not found"**
```bash
# Lösung: Überprüfe KEYSTORE_BASE64 Secret
echo $KEYSTORE_BASE64 | base64 --decode > test.keystore
keytool -list -keystore test.keystore
```

**Problem: "Wrong password"**
```bash
# Lösung: Überprüfe KEYSTORE_PASSWORD und KEY_PASSWORD Secrets
```

**Problem: "Build fails"**
```bash
# Lösung: Teste lokalen Build erst:
./gradlew assembleRelease
```
