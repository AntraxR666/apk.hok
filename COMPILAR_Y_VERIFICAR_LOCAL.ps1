param()

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

function Step([string]$Text) {
    Write-Host "`n=== $Text ===" -ForegroundColor Cyan
}

try {
    Step "LOCATING JDK 17"
    $candidates = @(
        "$env:LOCALAPPDATA\HoKBuildTools\jdk17",
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java"
    )
    $javaExe = foreach ($root in $candidates) {
        if (Test-Path -LiteralPath $root) {
            Get-ChildItem -LiteralPath $root -Filter java.exe -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -match '\\bin\\java\.exe$' }
        }
    } | Select-Object -First 1
    if (-not $javaExe) {
        throw "JDK 17 was not found. The previous HoK build tools installation is required."
    }
    $javaBin = Split-Path $javaExe.FullName -Parent
    $env:JAVA_HOME = Split-Path $javaBin -Parent
    $env:Path = "$javaBin;$env:Path"
    $javaVersion = & $javaExe.FullName -version 2>&1 | Out-String
    if ($javaVersion -notmatch 'version "17\.') {
        throw "Selected Java is not JDK 17: $javaVersion"
    }
    Write-Host "JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green

    Step "CHECKING ANDROID SDK"
    if (-not (Test-Path -LiteralPath ".\local.properties" -PathType Leaf)) {
        $sdk = "$env:LOCALAPPDATA\Android\Sdk"
        if (-not (Test-Path -LiteralPath $sdk -PathType Container)) {
            throw "Android SDK not found at $sdk"
        }
        $sdkGradle = $sdk.Replace('\','/')
        [IO.File]::WriteAllText((Join-Path $ProjectRoot 'local.properties'), "sdk.dir=$sdkGradle`r`n", (New-Object Text.UTF8Encoding($false)))
    }

    Step "STATIC VALIDATION"
    foreach ($script in @(
        "tools\validate_project.py",
        "tools\validate_v4.py",
        "tools\test_ui_contract.py",
        "tools\test_overlay_contract.py",
        "tools\test_overlay_scroll_contract.py",
        "tools\test_player_slot_detection_contract.py",
        "tools\test_player_pick_override_contract.py",
        "tools\test_stage_control_contract.py",
        "tools\test_calibration_contract.py"
    )) {
        & python $script
        if ($LASTEXITCODE -ne 0) { throw "Validation failed: $script" }
    }

    Step "GRADLE TEST, LINT AND APK"
    $logPath = Join-Path $ProjectRoot "build-output-v4.3.txt"
    & .\gradlew.bat --no-daemon clean testDebugUnitTest lintDebug assembleDebug --stacktrace 2>&1 | Tee-Object -FilePath $logPath
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed. Review $logPath"
    }

    Step "VERIFYING APK"
    $apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "APK was not generated: $apk"
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $names = @($archive.Entries | ForEach-Object FullName)
        foreach ($entry in @("AndroidManifest.xml", "classes.dex", "assets/hok_counters.json")) {
            if ($names -notcontains $entry) { throw "APK missing $entry" }
        }
    }
    finally { $archive.Dispose() }
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
    $desktop = Join-Path ([Environment]::GetFolderPath('Desktop')) "HoK_Draft_Assistant_V4_3_RC1_VERIFIED.apk"
    Copy-Item -LiteralPath $apk -Destination $desktop -Force
    Write-Host "APK verified: $desktop" -ForegroundColor Green
    Write-Host "SHA-256: $hash" -ForegroundColor Green
    exit 0
}
catch {
    Write-Host "`n=== PROCESS STOPPED ===" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    exit 1
}
