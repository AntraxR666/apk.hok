param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Repo = "AntraxR666/apk.hok"
$RemoteUrl = "https://github.com/AntraxR666/apk.hok.git"
$Source = Split-Path -Parent $MyInvocation.MyCommand.Path
$ArtifactName = "HoK-Draft-Assistant-V5-Huawei-JKM-LX3"
$Desktop = [Environment]::GetFolderPath("Desktop")
$Stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$LogPath = Join-Path $Desktop ("HoK_V5_Huawei_V3_" + $Stamp + ".log")
$WorkDir = Join-Path $env:TEMP ("HoK_V5_Huawei_V3_" + [Guid]::NewGuid().ToString("N"))
$TranscriptStarted = $false
$ExitCode = 1
$Success = $false

$Files = @(
    ".github/workflows/android-ci.yml"
    "README.md"
    "app/build.gradle"
    "build.gradle"
    "gradle/wrapper/gradle-wrapper.properties"
    "app/src/main/kotlin/com/example/honorofkingsassistant/AssistantSessionBus.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/PersonalDeviceProfile.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/AdaptiveFrameCadence.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/AssistantStage.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/CaptureGeometry.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/DraftModels.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/HeroPortraitMatcher.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/OcrBitmapPreprocessor.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/PortraitMatchSelector.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/TemporalDraftTracker.kt"
    "app/src/main/kotlin/com/example/honorofkingsassistant/VisionDiagnostics.kt"
    "app/src/main/res/values/strings.xml"
    "app/src/test/java/com/example/honorofkingsassistant/CaptureGeometryTest.kt"
    "app/src/test/java/com/example/honorofkingsassistant/AdaptiveFrameCadenceTest.kt"
    "app/src/test/java/com/example/honorofkingsassistant/RecognitionConfidenceTest.kt"
    "app/src/test/java/com/example/honorofkingsassistant/VisionDiagnosticsTest.kt"
    "docs/V5_PERSONAL_REAL_DEVICE_ACCEPTANCE.md"
    "docs/V5_PERSONAL_VERIFICATION_REPORT.md"
    "docs/superpowers/plans/2026-07-24-v5-personal-optimized.md"
    "docs/superpowers/specs/2026-07-24-v5-personal-optimized-design.md"
    "tools/capture_geometry_smoke_test.kt"
    "tools/adaptive_frame_cadence_smoke_test.kt"
    "tools/recognition_confidence_smoke_test.kt"
    "tools/test_overlay_contract.py"
    "tools/test_overlay_layout_params_contract.py"
    "tools/test_v5_build_contract.py"
    "tools/test_v5_background_vision_contract.py"
    "tools/test_v5_diagnostics_contract.py"
    "tools/test_v5_delivery_contract.py"
    "tools/test_v5_huawei_profile_contract.py"
    "tools/test_v5_recognition_contract.py"
    "tools/test_v5_single_fgs_contract.py"
    "tools/validate_project.py"
    "tools/validate_v4.py"
    "tools/validate_v5.py"
    "tools/vision_diagnostics_smoke_test.kt"
    "PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.ps1"
    "PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.bat"
)

function Step([string]$Text) {
    Write-Host "`n=== $Text ===" -ForegroundColor Cyan
}

function Fail([string]$Message) {
    throw $Message
}

function Require-File([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail ("Missing " + $Label + ": " + $Path)
    }
}

function Run-Python([string]$ScriptPath, [string]$Mode) {
    if ($Mode -eq "py") {
        & py -3 $ScriptPath
    } else {
        & python $ScriptPath
    }
    if ($LASTEXITCODE -ne 0) {
        Fail ("Python validation failed: " + $ScriptPath)
    }
}

try {
    Start-Transcript -LiteralPath $LogPath -Force | Out-Null
    $TranscriptStarted = $true

    Step "PREFLIGHT"
    foreach ($CommandName in @("git", "gh")) {
        if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
            Fail ($CommandName + " is not installed or is not available in PATH.")
        }
    }

    & gh auth status --hostname github.com
    if ($LASTEXITCODE -ne 0) {
        Fail "GitHub CLI is not authenticated. Run gh auth login and execute this launcher again."
    }

    & gh auth setup-git
    if ($LASTEXITCODE -ne 0) {
        Fail "GitHub CLI could not configure Git authentication."
    }

    & gh repo view $Repo --json nameWithOwner,defaultBranchRef --jq '.nameWithOwner'
    if ($LASTEXITCODE -ne 0) {
        Fail ("GitHub repository is not accessible: " + $Repo)
    }

    foreach ($Relative in $Files) {
        Require-File (Join-Path $Source $Relative) "V5 source file"
    }

    $PackageManifest = Join-Path $Source "PACKAGE_MANIFEST.sha256"
    Require-File $PackageManifest "package checksum manifest"
    foreach ($Line in Get-Content -LiteralPath $PackageManifest) {
        if ([string]::IsNullOrWhiteSpace($Line)) { continue }
        if ($Line -notmatch '^([0-9a-f]{64})  (.+)$') {
            Fail ("Invalid package manifest line: " + $Line)
        }
        $ExpectedHash = $Matches[1]
        $RelativePath = $Matches[2]
        $ManifestFile = Join-Path $Source $RelativePath
        Require-File $ManifestFile "manifest file"
        $ActualHash = (Get-FileHash -LiteralPath $ManifestFile -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($ActualHash -ne $ExpectedHash) {
            Fail ("Package checksum mismatch: " + $RelativePath)
        }
    }
    Write-Host "Package checksums: OK" -ForegroundColor Green

    Step "CREATE CLEAN GITHUB WORKSPACE"
    Write-Host ("Temporary workspace: " + $WorkDir)
    & git clone --branch main --single-branch $RemoteUrl $WorkDir
    if ($LASTEXITCODE -ne 0) {
        Fail "A clean clone of AntraxR666/apk.hok could not be created."
    }

    if (-not (Test-Path -LiteralPath (Join-Path $WorkDir ".git") -PathType Container)) {
        Fail "The clean clone does not contain a .git directory."
    }

    $Origin = (& git -C $WorkDir remote get-url origin | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $Origin -notmatch 'AntraxR666/apk[.]hok') {
        Fail ("The clean clone has an unexpected origin remote: " + $Origin)
    }
    Write-Host ("origin: " + $Origin) -ForegroundColor Green

    $GitLogin = (& gh api user --jq '.login' | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($GitLogin)) {
        $GitLogin = "AntraxR666"
    }
    & git -C $WorkDir config user.name $GitLogin
    if ($LASTEXITCODE -ne 0) { Fail "Could not configure git user.name." }
    & git -C $WorkDir config user.email ($GitLogin + "@users.noreply.github.com")
    if ($LASTEXITCODE -ne 0) { Fail "Could not configure git user.email." }

    Step "APPLY V5 HUAWEI JKM-LX3"
    foreach ($Relative in $Files) {
        $SourceFile = Join-Path $Source $Relative
        $TargetFile = Join-Path $WorkDir $Relative
        $TargetParent = Split-Path -Parent $TargetFile
        if (-not (Test-Path -LiteralPath $TargetParent -PathType Container)) {
            New-Item -ItemType Directory -Force -Path $TargetParent | Out-Null
        }
        Copy-Item -LiteralPath $SourceFile -Destination $TargetFile -Force
    }

    Step "LOCAL CONTRACT CHECKS"
    $AppGradle = Get-Content -LiteralPath (Join-Path $WorkDir "app\build.gradle") -Raw
    $Manifest = Get-Content -LiteralPath (Join-Path $WorkDir "app\src\main\AndroidManifest.xml") -Raw
    if ($AppGradle -notmatch "compileSdk 36" -or
        $AppGradle -notmatch "targetSdk 36" -or
        $AppGradle -notmatch 'versionName "5[.]0-personal-jkm-lx3-rc1"') {
        Fail "V5 Gradle metadata validation failed."
    }
    if ($Manifest -match "android[.]permission[.]INTERNET") {
        Fail "Unexpected INTERNET permission detected."
    }

    $PythonMode = $null
    if (Get-Command py -ErrorAction SilentlyContinue) {
        $PythonMode = "py"
    } elseif (Get-Command python -ErrorAction SilentlyContinue) {
        $PythonMode = "python"
    }

    Push-Location $WorkDir
    try {
        if ($null -ne $PythonMode) {
            foreach ($Validator in @(
                "tools\validate_project.py",
                "tools\validate_v4.py",
                "tools\test_overlay_contract.py",
                "tools\test_overlay_layout_params_contract.py",
                "tools\test_v5_build_contract.py",
                "tools\test_v5_background_vision_contract.py",
                "tools\test_v5_recognition_contract.py",
                "tools\test_v5_diagnostics_contract.py",
                "tools\test_v5_single_fgs_contract.py",
                "tools\test_v5_delivery_contract.py",
                "tools\test_v5_huawei_profile_contract.py",
                "tools\validate_v5.py"
            )) {
                Run-Python (Join-Path $WorkDir $Validator) $PythonMode
            }
        } else {
            Write-Host "Python was not found. GitHub CI will run the complete validator set." -ForegroundColor Yellow
        }

        & git diff --check
        if ($LASTEXITCODE -ne 0) { Fail "git diff --check failed." }

        Step "COMMIT AND PUSH"
        & git add -A
        if ($LASTEXITCODE -ne 0) { Fail "git add failed." }

        & git diff --cached --quiet
        $HasChanges = $LASTEXITCODE -ne 0
        $ManualRunRequired = $false
        $PreviousManualRunId = ""

        if ($HasChanges) {
            & git commit -m "release: prepare V5 Huawei JKM-LX3 personal candidate"
            if ($LASTEXITCODE -ne 0) { Fail "git commit failed." }
            & git push origin HEAD:main
            if ($LASTEXITCODE -ne 0) { Fail "git push failed." }
        } else {
            Write-Host "V5 files already match main. A fresh workflow run will be requested." -ForegroundColor Yellow
            $ManualRunRequired = $true
            $PreviousManualRunId = (& gh run list --repo $Repo --workflow "Android CI" --event workflow_dispatch --branch main --limit 1 --json databaseId --jq '.[0].databaseId' 2>$null | Out-String).Trim()
            & gh workflow run "android-ci.yml" --repo $Repo --ref main
            if ($LASTEXITCODE -ne 0) { Fail "Could not request a fresh Android CI run." }
        }

        $Commit = (& git rev-parse HEAD | Out-String).Trim()
        if ($Commit -notmatch '^[0-9a-f]{40}$') {
            Fail ("Invalid commit SHA: " + $Commit)
        }
        Write-Host ("Commit: " + $Commit) -ForegroundColor Green

        Step "WAIT FOR ANDROID CI"
        $RunId = ""
        if ($ManualRunRequired) {
            for ($Attempt = 1; $Attempt -le 90; $Attempt++) {
                $CandidateRunId = (& gh run list --repo $Repo --workflow "Android CI" --event workflow_dispatch --branch main --limit 1 --json databaseId --jq '.[0].databaseId' 2>$null | Out-String).Trim()
                if ($CandidateRunId -match '^[0-9]+$' -and $CandidateRunId -ne $PreviousManualRunId) {
                    $RunId = $CandidateRunId
                    break
                }
                Start-Sleep -Seconds 2
            }
        } else {
            for ($Attempt = 1; $Attempt -le 90; $Attempt++) {
                $CandidateRunId = (& gh run list --repo $Repo --commit $Commit --workflow "Android CI" --limit 1 --json databaseId --jq '.[0].databaseId' 2>$null | Out-String).Trim()
                if ($CandidateRunId -match '^[0-9]+$') {
                    $RunId = $CandidateRunId
                    break
                }
                Start-Sleep -Seconds 2
            }
        }

        if ($RunId -notmatch '^[0-9]+$') {
            Fail ("GitHub did not create an Android CI run for commit " + $Commit)
        }

        $RunUrl = (& gh run view $RunId --repo $Repo --json url --jq '.url' | Out-String).Trim()
        Write-Host ("Run ID: " + $RunId)
        Write-Host ("Run URL: " + $RunUrl)

        & gh run watch $RunId --repo $Repo --exit-status
        $WatchExit = $LASTEXITCODE
        $Conclusion = (& gh run view $RunId --repo $Repo --json conclusion --jq '.conclusion' | Out-String).Trim()
        if ($WatchExit -ne 0 -or $Conclusion -ne "success") {
            Write-Host "`n=== FAILED CI LOGS ===" -ForegroundColor Red
            & gh run view $RunId --repo $Repo --log-failed
            Fail ("Android CI ended with: " + $Conclusion)
        }

        Step "DOWNLOAD AND VERIFY APK"
        $ArtifactDir = Join-Path $Desktop ("HoK_V5_HUAWEI_JKM_LX3_" + $Stamp)
        New-Item -ItemType Directory -Force -Path $ArtifactDir | Out-Null
        & gh run download $RunId --repo $Repo --name $ArtifactName --dir $ArtifactDir
        if ($LASTEXITCODE -ne 0) { Fail "Artifact download failed." }

        $Apk = Get-ChildItem -LiteralPath $ArtifactDir -Filter "*.apk" -Recurse -File | Select-Object -First 1
        if ($null -eq $Apk) { Fail "The successful artifact does not contain an APK." }

        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $Archive = [System.IO.Compression.ZipFile]::OpenRead($Apk.FullName)
        try {
            $Entries = @($Archive.Entries | ForEach-Object { $_.FullName })
            foreach ($RequiredEntry in @("AndroidManifest.xml", "classes.dex", "assets/hok_counters.json")) {
                if ($Entries -notcontains $RequiredEntry) {
                    Fail ("APK missing required entry: " + $RequiredEntry)
                }
            }
        } finally {
            $Archive.Dispose()
        }

        $FinalApk = Join-Path $Desktop "HoK_Draft_Assistant_V5_HUAWEI_JKM_LX3.apk"
        Copy-Item -LiteralPath $Apk.FullName -Destination $FinalApk -Force
        $Hash = Get-FileHash -LiteralPath $FinalApk -Algorithm SHA256
        $Size = (Get-Item -LiteralPath $FinalApk).Length

        Write-Host "`n========================================" -ForegroundColor Green
        Write-Host " V5 HUAWEI JKM-LX3 APK READY" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ("APK: " + $FinalApk)
        Write-Host ("Size: " + [math]::Round($Size / 1MB, 2) + " MB")
        Write-Host ("SHA-256: " + $Hash.Hash)
        Write-Host ("Commit: " + $Commit)
        Write-Host ("Run: " + $RunUrl)
        Write-Host ("Log: " + $LogPath)
        $Success = $true
        $ExitCode = 0
    } finally {
        Pop-Location
    }
} catch {
    Write-Host "`n========================================" -ForegroundColor Red
    Write-Host " V5 HUAWEI PROCESS STOPPED - NO VALID APK" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    Write-Host ("Log: " + $LogPath) -ForegroundColor Yellow
    Write-Host ("Temporary workspace kept for diagnosis: " + $WorkDir) -ForegroundColor Yellow
    $ExitCode = 1
} finally {
    if ($Success -and (Test-Path -LiteralPath $WorkDir -PathType Container)) {
        Remove-Item -LiteralPath $WorkDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if ($TranscriptStarted) {
        try { Stop-Transcript | Out-Null } catch { }
    }
}

exit $ExitCode
