param(
    [string]$Destination = "$env:USERPROFILE\Documents\HoK_Counter_App"
)

$ErrorActionPreference = "Stop"
$Source = Split-Path -Parent $MyInvocation.MyCommand.Path
$TimeStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$Backup = "${Destination}_backup_v4_${TimeStamp}"

function Step([string]$Text) {
    Write-Host "`n=== $Text ===" -ForegroundColor Cyan
}

function Require-File([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing $Label`: $Path"
    }
}

try {
    Step "CHECKING EXISTING PROJECT"
    if (-not (Test-Path -LiteralPath $Destination -PathType Container)) {
        throw "Project folder not found: $Destination"
    }
    Require-File (Join-Path $Destination "gradlew.bat") "Gradle wrapper"
    Require-File (Join-Path $Destination "local.properties") "Android SDK configuration"

    Step "CREATING BACKUP"
    New-Item -ItemType Directory -Force -Path $Backup | Out-Null
    & robocopy $Destination $Backup /E /R:1 /W:1 /XD ".gradle" "build" "app\build" ".git" | Out-Null
    if ($LASTEXITCODE -gt 7) {
        throw "Backup failed with robocopy code $LASTEXITCODE"
    }
    Write-Host "Backup: $Backup" -ForegroundColor Green

    Step "APPLYING V4.3 RC1 SOURCE"
    $sourceApp = Join-Path $Source "app"
    $destinationApp = Join-Path $Destination "app"
    & robocopy $sourceApp $destinationApp /E /R:1 /W:1 /XD "build" | Out-Null
    if ($LASTEXITCODE -gt 7) {
        throw "App copy failed with robocopy code $LASTEXITCODE"
    }
    foreach ($name in @("build.gradle", "settings.gradle", "gradle.properties", ".gitignore")) {
        $sourceFile = Join-Path $Source $name
        if (Test-Path -LiteralPath $sourceFile) {
            Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $Destination $name) -Force
        }
    }
    foreach ($folder in @("tools", "docs")) {
        $sourceFolder = Join-Path $Source $folder
        if (Test-Path -LiteralPath $sourceFolder) {
            & robocopy $sourceFolder (Join-Path $Destination $folder) /E /R:1 /W:1 | Out-Null
            if ($LASTEXITCODE -gt 7) {
                throw "Copy failed for $folder with robocopy code $LASTEXITCODE"
            }
        }
    }

    Step "VALIDATING APPLIED SOURCE"
    $manifestPath = Join-Path $Destination "app\src\main\AndroidManifest.xml"
    $catalogPath = Join-Path $Destination "app\src\main\assets\hok_counters.json"
    $gradlePath = Join-Path $Destination "app\build.gradle"
    $videoTestPath = Join-Path $Destination "app\src\test\java\com\example\honorofkingsassistant\VideoCalibrationTest.kt"
    Require-File $manifestPath "AndroidManifest.xml"
    Require-File $catalogPath "hero catalog"
    Require-File $gradlePath "app build.gradle"
    Require-File $videoTestPath "video calibration unit test"

    $manifestText = Get-Content -LiteralPath $manifestPath -Raw
    if ($manifestText -notmatch 'foregroundServiceType="mediaProjection"') {
        throw "MediaProjection foreground service type is missing from AndroidManifest.xml"
    }
    if ($manifestText -match 'android.permission.INTERNET') {
        throw "Unexpected INTERNET permission detected"
    }
    $gradleText = Get-Content -LiteralPath $gradlePath -Raw
    if ($gradleText -notmatch 'versionName\s+"4\.3-rc1-video-calibrated"') {
        throw "Unexpected app version; expected 4.3-rc1-video-calibrated"
    }

    $catalog = Get-Content -LiteralPath $catalogPath -Raw | ConvertFrom-Json
    $heroCount = @($catalog.heroes).Count
    $relationCount = 0
    foreach ($hero in @($catalog.heroes)) {
        $relationCount += @($hero.counters).Count
    }
    if ($heroCount -ne 116 -or $relationCount -ne 348) {
        throw "Catalog validation failed: heroes=$heroCount relations=$relationCount"
    }
    Write-Host "Source OK: 116 heroes, 348 counter relations, V4.3 video tests present." -ForegroundColor Green

    Step "CONFIGURING JDK 17"
    $jdkRoot = Join-Path $env:LOCALAPPDATA "HoKBuildTools\jdk17"
    $javaExe = Get-ChildItem -Path $jdkRoot -Filter "java.exe" -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "\\bin\\java\.exe$" } |
        Select-Object -First 1
    if (-not $javaExe) {
        throw "JDK 17 was not found in $jdkRoot"
    }
    $javaBin = Split-Path $javaExe.FullName -Parent
    $env:JAVA_HOME = Split-Path $javaBin -Parent
    $env:Path = "$javaBin;$env:Path"
    $javaVersion = & $javaExe.FullName -version 2>&1 | Out-String
    if ($javaVersion -notmatch 'version "17\.') {
        throw "The selected Java runtime is not JDK 17: $javaVersion"
    }
    Write-Host "JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green

    Step "BUILDING AND TESTING V4.3 RC1"
    $logPath = Join-Path $Destination "build-output-v4.txt"
    Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue
    $command = 'call gradlew.bat clean testDebugUnitTest assembleDebug --stacktrace --no-daemon > build-output-v4.txt 2>&1'
    $process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/s', '/c', $command) -WorkingDirectory $Destination -Wait -PassThru -NoNewWindow
    if (Test-Path -LiteralPath $logPath) {
        Get-Content -LiteralPath $logPath -Tail 100
    }
    if ($process.ExitCode -ne 0) {
        throw "Gradle failed with code $($process.ExitCode). Review $logPath"
    }

    Step "VERIFYING TEST RESULTS"
    $resultFolder = Join-Path $Destination "app\build\test-results\testDebugUnitTest"
    $testFiles = @(Get-ChildItem -LiteralPath $resultFolder -Filter "TEST-*.xml" -File -ErrorAction SilentlyContinue)
    if ($testFiles.Count -eq 0) {
        throw "No JUnit XML reports were generated in $resultFolder"
    }
    $tests = 0
    $failures = 0
    $errors = 0
    foreach ($file in $testFiles) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
        $tests += [int]$xml.testsuite.tests
        $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors
    }
    if ($failures -ne 0 -or $errors -ne 0) {
        throw "JUnit verification failed: tests=$tests failures=$failures errors=$errors"
    }
    Write-Host "JUnit OK: $tests tests, 0 failures, 0 errors." -ForegroundColor Green

    Step "VERIFYING APK CONTENT"
    $apk = Join-Path $Destination "app\build\outputs\apk\debug\app-debug.apk"
    Require-File $apk "compiled APK"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        foreach ($requiredEntry in @("AndroidManifest.xml", "classes.dex", "assets/hok_counters.json")) {
            if ($entryNames -notcontains $requiredEntry) {
                throw "APK is missing required entry: $requiredEntry"
            }
        }
    }
    finally {
        $archive.Dispose()
    }

    $desktopApk = Join-Path ([Environment]::GetFolderPath('Desktop')) "HoK_Draft_Assistant_V4_3_RC1_VIDEO_CALIBRATED.apk"
    Copy-Item -LiteralPath $apk -Destination $desktopApk -Force
    $apkInfo = Get-Item -LiteralPath $desktopApk

    Step "V4.3 RC1 VIDEO CALIBRATION COMPLETE"
    Write-Host "APK: $apk" -ForegroundColor Green
    Write-Host "Desktop copy: $desktopApk" -ForegroundColor Green
    Write-Host "Size: $([math]::Round($apkInfo.Length / 1MB, 2)) MB" -ForegroundColor Green
    Write-Host "Build log: $logPath" -ForegroundColor Green
    exit 0
}
catch {
    Write-Host "`n=== PROCESS STOPPED ===" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    Write-Host "Backup remains at: $Backup" -ForegroundColor Yellow
    exit 1
}
