# Android Icon Generator Script
# Erstellt alle benötigten Icon-Größen aus dem Basis-Icon

param(
    [string]$SourceIcon = "Icon.png",
    [string]$OutputDir = "app\src\main\res"
)

Write-Host "🎨 Android Icon Generator gestartet..." -ForegroundColor Green
Write-Host "Quelle: $SourceIcon" -ForegroundColor Cyan

# Prüfe ob Source-Icon existiert
if (-not (Test-Path $SourceIcon)) {
    Write-Error "❌ Icon-Datei '$SourceIcon' nicht gefunden!"
    exit 1
}

# Funktion zur Icon-Generierung mit .NET System.Drawing
function Generate-Icon {
    param(
        [string]$SourcePath,
        [string]$TargetPath,
        [int]$Width,
        [int]$Height
    )
    
    try {
        # Lade .NET Bibliotheken
        Add-Type -AssemblyName System.Drawing
        
        # Erstelle Zielverzeichnis
        $targetDir = Split-Path $TargetPath -Parent
        if (-not (Test-Path $targetDir)) {
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }
        
        # Lade Quelle-Bild
        $sourceImage = [System.Drawing.Image]::FromFile((Resolve-Path $SourcePath))
        
        # Erstelle neue Bitmap mit gewünschter Größe
        $resizedImage = New-Object System.Drawing.Bitmap($Width, $Height)
        $graphics = [System.Drawing.Graphics]::FromImage($resizedImage)
        
        # Setze hohe Qualität
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        
        # Zeichne resized Bild
        $graphics.DrawImage($sourceImage, 0, 0, $Width, $Height)
        
        # Speichere als PNG
        $resizedImage.Save($TargetPath, [System.Drawing.Imaging.ImageFormat]::Png)
        
        # Cleanup
        $graphics.Dispose()
        $resizedImage.Dispose()
        $sourceImage.Dispose()
        
        Write-Host "✅ Erstellt: $TargetPath ($Width×$Height)" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "❌ Fehler beim Erstellen von $TargetPath`: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# Android Icon-Spezifikationen
$iconSizes = @(
    @{ density = "mdpi"; size = 48; folder = "mipmap-mdpi" },
    @{ density = "hdpi"; size = 72; folder = "mipmap-hdpi" },
    @{ density = "xhdpi"; size = 96; folder = "mipmap-xhdpi" },
    @{ density = "xxhdpi"; size = 144; folder = "mipmap-xxhdpi" },
    @{ density = "xxxhdpi"; size = 192; folder = "mipmap-xxxhdpi" }
)

$successCount = 0
$totalCount = $iconSizes.Count

Write-Host "📱 Generiere Android Icons..." -ForegroundColor Yellow

foreach ($iconSpec in $iconSizes) {
    $targetFolder = Join-Path $OutputDir $iconSpec.folder
    $targetPath = Join-Path $targetFolder "ic_launcher.png"
    $targetRoundPath = Join-Path $targetFolder "ic_launcher_round.png"
    
    # Standard Icon
    if (Generate-Icon -SourcePath $SourceIcon -TargetPath $targetPath -Width $iconSpec.size -Height $iconSpec.size) {
        $successCount++
    }
    
    # Round Icon (kopiere standard icon - kann später angepasst werden)
    if (Generate-Icon -SourcePath $SourceIcon -TargetPath $targetRoundPath -Width $iconSpec.size -Height $iconSpec.size) {
        # Erfolg bereits in successCount gezählt
    }
}

# PlayStore Icon (512x512)
$playStoreIcon = "ic_launcher_playstore.png"
Write-Host "🏪 Generiere PlayStore Icon (512×512)..." -ForegroundColor Yellow
if (Generate-Icon -SourcePath $SourceIcon -TargetPath $playStoreIcon -Width 512 -Height 512) {
    $successCount++
    Write-Host "✅ PlayStore Icon erstellt: $playStoreIcon" -ForegroundColor Green
}

# Zusammenfassung
Write-Host "`n📊 ZUSAMMENFASSUNG:" -ForegroundColor Cyan
Write-Host "Erfolgreich erstellt: $successCount/$totalCount Icon-Größen" -ForegroundColor Green
Write-Host "PlayStore Icon: ic_launcher_playstore.png" -ForegroundColor Cyan

if ($successCount -eq $totalCount) {
    Write-Host "`n🎉 Alle Icons erfolgreich generiert!" -ForegroundColor Green
    Write-Host "Nächste Schritte:" -ForegroundColor Yellow
    Write-Host "1. Backup der alten .webp Dateien (optional)" -ForegroundColor White
    Write-Host "2. App neu kompilieren" -ForegroundColor White
    Write-Host "3. Icons auf Gerät testen" -ForegroundColor White
} else {
    Write-Host "`n⚠️  Einige Icons konnten nicht erstellt werden!" -ForegroundColor Yellow
    Write-Host "Bitte Log prüfen und ggf. manuell nachbearbeiten." -ForegroundColor White
}

Write-Host "`n🏁 Icon-Generator beendet." -ForegroundColor Cyan
