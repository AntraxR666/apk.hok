param(
    [string]$Owner = "AntraxR666",
    [string]$RepoName = "apk.hok",
    [switch]$NoForce
)

$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
    $Global:PSNativeCommandUseErrorActionPreference = $false
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

function Step([string]$Text) {
    Write-Host "`n=== $Text ===" -ForegroundColor Cyan
}

function NeedCommand([string]$Name, [string]$WingetId) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        if (Get-Command winget -ErrorAction SilentlyContinue) {
            Write-Host "$Name not found. Installing $WingetId with winget..." -ForegroundColor Yellow
            winget install --id $WingetId --exact --accept-source-agreements --accept-package-agreements
            if ($LASTEXITCODE -ne 0) { throw "winget install failed for $WingetId" }
        } else {
            throw "$Name is not installed and winget is unavailable."
        }
    }
}

try {
    Step "CHECKING PROJECT FILES"
    foreach ($required in @(
        ".\gradlew.bat",
        ".\gradle\wrapper\gradle-wrapper.jar",
        ".\.github\workflows\android-ci.yml",
        ".\app\build.gradle",
        ".\app\src\main\AndroidManifest.xml",
        ".\app\src\main\assets\hok_counters.json"
    )) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
            throw "Missing required project file: $required"
        }
    }

    Step "CHECKING GIT"
    NeedCommand "git" "Git.Git"

    Step "CHECKING GITHUB CLI"
    NeedCommand "gh" "GitHub.cli"
    $env:Path = "$env:ProgramFiles\GitHub CLI;$env:Path"

    Step "GITHUB LOGIN"
    $authOutput = (& gh auth status --hostname github.com 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        Write-Host "You are not logged in to GitHub CLI yet." -ForegroundColor Yellow
        Write-Host "Choose: GitHub.com -> HTTPS -> Yes -> Login with a web browser." -ForegroundColor Yellow
        & gh auth login --hostname github.com --git-protocol https --web
        if ($LASTEXITCODE -ne 0) {
            throw "GitHub login failed. Run manually: gh auth login"
        }
    } else {
        Write-Host "GitHub CLI is already logged in." -ForegroundColor Green
    }

    $login = (& gh api user --jq .login).Trim()
    if (-not $login) { throw "Could not read GitHub login." }
    if ($login -ne $Owner) {
        Write-Host "Authenticated as $login. Using $login as owner." -ForegroundColor Yellow
        $Owner = $login
    }

    $fullRepo = "$Owner/$RepoName"

    Step "VERIFYING TARGET REPOSITORY $fullRepo"
    & gh repo view $fullRepo --json nameWithOwner,url,defaultBranchRef
    if ($LASTEXITCODE -ne 0) {
        throw "Repository $fullRepo is not accessible from GitHub CLI."
    }

    Step "INITIALIZING LOCAL GIT"
    if (-not (Test-Path -LiteralPath ".\.git" -PathType Container)) {
        & git init -b main
        if ($LASTEXITCODE -ne 0) { throw "git init failed." }
    }

    & git config user.name $Owner
    $emailRaw = (& gh api user --jq '.email // empty' 2>$null | Out-String)
    $email = if ($null -eq $emailRaw) { "" } else { $emailRaw.Trim() }
    if ([string]::IsNullOrWhiteSpace($email)) { $email = "$Owner@users.noreply.github.com" }
    & git config user.email $email

    Step "ADDING PROJECT FILES"
    & git add -A
    if ($LASTEXITCODE -ne 0) { throw "git add failed." }

    $pending = (& git status --porcelain | Out-String).Trim()
    if ($pending) {
        & git commit -m "feat: add HoK Draft Assistant V4.3 RC1"
        if ($LASTEXITCODE -ne 0) { throw "git commit failed." }
    } else {
        Write-Host "No local file changes to commit." -ForegroundColor Yellow
    }

    Step "CONFIGURING REMOTE"
    $remoteUrl = "https://github.com/$fullRepo.git"
    $origin = (& git remote get-url origin 2>$null | Out-String).Trim()
    if (-not $origin) {
        & git remote add origin $remoteUrl
    } else {
        & git remote set-url origin $remoteUrl
    }
    & git branch -M main

    Step "PUSHING PROJECT TO $fullRepo"
    if ($NoForce) {
        & git push -u origin main
    } else {
        Write-Host "The existing repository currently contains only the uploaded ZIP. It will be replaced by the extracted project." -ForegroundColor Yellow
        & git push -u origin main --force
    }
    if ($LASTEXITCODE -ne 0) { throw "git push failed." }

    Step "SHOWING ACTIONS"
    & gh workflow list --repo $fullRepo
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Workflow list failed. It may take a minute for GitHub to detect the workflow." -ForegroundColor Yellow
    }

    Step "DONE"
    Write-Host "Repository updated: https://github.com/$fullRepo" -ForegroundColor Green
    Write-Host "Open Actions: https://github.com/$fullRepo/actions" -ForegroundColor Green
    Write-Host "Wait for Android CI. Then return to ChatGPT and write: listo" -ForegroundColor Green
}
catch {
    Write-Host "`nERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Copy this whole window text and send it to ChatGPT." -ForegroundColor Yellow
    exit 1
}
