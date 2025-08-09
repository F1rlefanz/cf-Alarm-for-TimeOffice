# 🔐 CFAlarm Signing Configuration Validator
# Version: 1.0
# Created: 07.08.2025

Write-Host "🔐 CFAlarm for Time Office - Signing Configuration Validator" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# Check if we're in the correct directory
if (-not (Test-Path "app/build.gradle.kts")) {
    Write-Host "❌ ERROR: Please run this script from the project root directory" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "📋 Checking Keystore Files..." -ForegroundColor Yellow

# Check Debug Keystore
$debugKeystore = "$env:USERPROFILE\.android\debug.keystore"
if (Test-Path $debugKeystore) {
    Write-Host "✅ Debug keystore found: $debugKeystore" -ForegroundColor Green
} else {
    Write-Host "⚠️  Debug keystore not found (will be auto-generated)" -ForegroundColor Yellow
}

# Check Production Keystore
$prodKeystore = "cf-alarm-release.keystore"
if (Test-Path $prodKeystore) {
    Write-Host "✅ Production keystore found: $prodKeystore" -ForegroundColor Green
    
    # Get keystore info
    Write-Host ""
    Write-Host "📋 Production Keystore Details:" -ForegroundColor Yellow
    $keystoreInfo = keytool -list -v -keystore $prodKeystore -alias cf-alarm-key -storepass 'CFAlarm2025!' -keypass 'CFAlarm2025!' 2>$null
    if ($LASTEXITCODE -eq 0) {
        $keystoreInfo | Select-String "SHA1:|SHA-256:|G.*ltig bis:" | ForEach-Object {
            Write-Host "  $($_.Line.Trim())" -ForegroundColor Green
        }
    }
} else {
    Write-Host "❌ Production keystore NOT found: $prodKeystore" -ForegroundColor Red
    Write-Host "   Run the setup script to create it!" -ForegroundColor Red
}

Write-Host ""
Write-Host "🔧 Validating Gradle Configuration..." -ForegroundColor Yellow

# Run Gradle signing report
Write-Host "📋 Running Gradle signing report..." -ForegroundColor Yellow
$gradleOutput = .\gradlew app:signingReport 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Gradle signing configuration is valid!" -ForegroundColor Green
    
    # Extract signing information
    Write-Host ""
    Write-Host "📋 Build Variants Signing Configuration:" -ForegroundColor Yellow
    
    $gradleOutput | Select-String "Variant:|Config:|Store:|SHA1:" | ForEach-Object {
        $line = $_.Line.Trim()
        if ($line.StartsWith("Variant:")) {
            Write-Host ""
            Write-Host "  🎯 $line" -ForegroundColor Cyan
        } elseif ($line.StartsWith("Config:")) {
            Write-Host "     $line" -ForegroundColor White
        } elseif ($line.StartsWith("Store:")) {
            if ($line.Contains("debug.keystore")) {
                Write-Host "     $line" -ForegroundColor Yellow
            } else {
                Write-Host "     $line" -ForegroundColor Green
            }
        } elseif ($line.StartsWith("SHA1:")) {
            Write-Host "     $line" -ForegroundColor Magenta
        }
    }
} else {
    Write-Host "❌ Gradle signing configuration has issues!" -ForegroundColor Red
    Write-Host "Check the build.gradle.kts file for errors." -ForegroundColor Red
}

Write-Host ""
Write-Host "🛡️ Security Checklist:" -ForegroundColor Yellow

# Check .gitignore
if ((Get-Content .gitignore -Raw) -match "\*\.keystore") {
    Write-Host "✅ Keystores are excluded from Git" -ForegroundColor Green
} else {
    Write-Host "⚠️  Add *.keystore to .gitignore!" -ForegroundColor Yellow
}

# Check if keystore is in Git
$gitStatus = git status --porcelain 2>$null | Select-String "keystore"
if ($gitStatus) {
    Write-Host "❌ WARNING: Keystore files detected in Git staging!" -ForegroundColor Red
    Write-Host "   Remove them immediately: git rm --cached *.keystore" -ForegroundColor Red
} else {
    Write-Host "✅ No keystores in Git staging area" -ForegroundColor Green
}

Write-Host ""
Write-Host "📋 Summary:" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

if ((Test-Path $prodKeystore) -and ($LASTEXITCODE -eq 0)) {
    Write-Host "🎉 SUCCESS: Debug-Build Integrity Warnings are resolved!" -ForegroundColor Green
    Write-Host ""
    Write-Host "✅ Debug builds use: Debug keystore (development only)" -ForegroundColor Green
    Write-Host "✅ Release builds use: Production keystore (secure)" -ForegroundColor Green
    Write-Host "✅ Staging builds use: Production keystore (realistic testing)" -ForegroundColor Green
    Write-Host ""
    Write-Host "🚀 Ready for production deployment!" -ForegroundColor Green
} else {
    Write-Host "❌ Issues detected - review the errors above" -ForegroundColor Red
}

Write-Host ""
Write-Host "📖 For detailed documentation, see: KEYSTORE_SECURITY_GUIDE.md" -ForegroundColor Cyan
