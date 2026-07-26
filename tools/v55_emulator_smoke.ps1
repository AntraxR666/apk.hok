param(
    [string]$Serial = "",
    [string]$Apk = "app\\build\\outputs\\apk\\debug\\app-debug.apk"
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param([string[]]$Arguments)
    & $script:Adb "-s" $script:Target @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
}

$SdkLine = Get-Content "local.properties" | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
if (-not $SdkLine) { throw "Missing sdk.dir in local.properties" }
$Sdk = ($SdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\\'
$script:Adb = Join-Path $Sdk "platform-tools\\adb.exe"
if (-not (Test-Path $script:Adb)) { throw "adb.exe was not found" }
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $Serial = (& $script:Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' } |
        ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1)
}
if ([string]::IsNullOrWhiteSpace($Serial)) { throw "No Android device or emulator is connected" }
$script:Target = $Serial.Trim()

$Package = "com.example.honorofkingsassistant"
$OutputDir = "build\\qa"
New-Item -ItemType Directory -Force $OutputDir | Out-Null

# Clearing emulator logs is optional; some API images reject it while still
# allowing the process-specific query used below.
& $script:Adb "-s" $script:Target logcat -c 2>$null | Out-Null
Invoke-Adb @("install", "-r", $Apk)
Invoke-Adb @("shell", "am", "force-stop", $Package)
Invoke-Adb @("shell", "monkey", "-p", $Package, "-c", "android.intent.category.LAUNCHER", "1")
Start-Sleep -Seconds 3
Invoke-Adb @("shell", "uiautomator", "dump", "/sdcard/hok-v1.xml")
Invoke-Adb @("pull", "/sdcard/hok-v1.xml", "$OutputDir\\hok-v1.xml")

$Activity = (& $script:Adb "-s" $script:Target shell dumpsys activity activities) -join "`n"
if ($Activity -notmatch "com\.example\.honorofkingsassistant/.MainActivity") {
    throw "MainActivity was not resumed"
}
$AssistantProcessId = ((& $script:Adb "-s" $script:Target shell pidof $Package) -join "").Trim()
if ([string]::IsNullOrWhiteSpace($AssistantProcessId)) { throw "Assistant process is not running" }
$Errors = (& $script:Adb "-s" $script:Target logcat -d --pid=$AssistantProcessId -v brief AndroidRuntime:E "*:S") -join "`n"
[System.IO.File]::WriteAllText("$OutputDir\\hok-v1-logcat-errors.txt", $Errors)
if ($Errors -match "FATAL EXCEPTION") { throw "AndroidRuntime reported a fatal exception" }

Write-Host "EMULATOR_SMOKE_OK"
Write-Host "Serial: $script:Target"
Write-Host "Process: $AssistantProcessId"
Write-Host "UI dump: $OutputDir\\hok-v1.xml"
