param(
    [string]$Owner = "AntraxR666",
    [string]$RepoName = "apk.hok",
    [switch]$Public
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

function Step([string]$Text) {
    Write-Host "`n=== $Text ===" -ForegroundColor Cyan
}

function Require-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required command not found: $Name"
    }
    return $command.Source
}

try {
    Step "CHECKING PROJECT"
    foreach ($required in @(
        ".\gradlew.bat",
        ".\gradle\wrapper\gradle-wrapper.jar",
        ".\.github\workflows\android-ci.yml",
        ".\app\build.gradle",
        ".\app\src\main\AndroidManifest.xml"
    )) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
            throw "Missing required project file: $required"
        }
    }

    Step "CHECKING GIT"
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        if (Get-Command winget -ErrorAction SilentlyContinue) {
            Write-Host "Git not found. Installing Git with winget..." -ForegroundColor Yellow
            winget install --id Git.Git --exact --accept-source-agreements --accept-package-agreements
        }
    }
    Require-Command "git" | Out-Null

    Step "CHECKING GITHUB CLI"
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        if (Get-Command winget -ErrorAction SilentlyContinue) {
            Write-Host "GitHub CLI not found. Installing it with winget..." -ForegroundColor Yellow
            winget install --id GitHub.cli --exact --accept-source-agreements --accept-package-agreements
            $env:Path = "$env:ProgramFiles\GitHub CLI;$env:Path"
        }
    }
    Require-Command "gh" | Out-Null

    Step "AUTHENTICATING WITH GITHUB"
    & gh auth status --hostname github.com 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "A browser window will open for GitHub authorization." -ForegroundColor Yellow
        & gh auth login --hostname github.com --web --git-protocol https
        if ($LASTEXITCODE -ne 0) {
            throw "GitHub authentication did not complete."
        }
    }

    $login = (& gh api user --jq .login).Trim()
    if (-not $login) {
        throw "Could not read the authenticated GitHub account."
    }
    if ($login -ne $Owner) {
        Write-Host "Authenticated account is $login; using it as repository owner." -ForegroundColor Yellow
        $Owner = $login
    }

    Step "PREPARING LOCAL GIT REPOSITORY"
    if (-not (Test-Path -LiteralPath ".\.git" -PathType Container)) {
        & git init -b main
    }
    & git config user.name $Owner
    $email = (& gh api user --jq '.email // empty').Trim()
    if (-not $email) {
        $email = "$Owner@users.noreply.github.com"
    }
    & git config user.email $email
    & git add -A
    $pending = (& git status --porcelain | Out-String).Trim()
    if ($pending) {
        & git commit -m "feat: add HoK Draft Assistant V4.3 RC1"
    }

    $fullRepo = "$Owner/$RepoName"
    Step "CREATING PRIVATE GITHUB REPOSITORY"
    & gh repo view $fullRepo --json nameWithOwner 2>$null | Out-Null
    $repoExists = ($LASTEXITCODE -eq 0)

    if (-not $repoExists) {
        $visibility = if ($Public) { "--public" } else { "--private" }
        & gh repo create $fullRepo $visibility --source . --remote origin --push --description "Local-first Honor of Kings draft assistant with overlay, OCR and video-calibrated draft tracking"
        if ($LASTEXITCODE -ne 0) {
            throw "GitHub repository creation or initial push failed."
        }
    }
    else {
        $origin = (& git remote get-url origin 2>$null | Out-String).Trim()
        if (-not $origin) {
            & git remote add origin "https://github.com/$fullRepo.git"
        }
        & git branch -M main
        & git push -u origin main
        if ($LASTEXITCODE -ne 0) {
            throw "Push to the existing repository failed."
        }
    }

    Step "WAITING FOR ANDROID CI"
    Start-Sleep -Seconds 5
    & gh run list --repo $fullRepo --workflow android-ci.yml --limit 1
    Write-Host "`nRepository: https://github.com/$fullRepo" -ForegroundColor Green
    Write-Host "Actions: https://github.com/$fullRepo/actions" -ForegroundColor Green
    Write-Host "`nThe push has triggered Android CI. You can close this window after reviewing the status." -ForegroundColor Green
    & gh repo view $fullRepo --web
    exit 0
}
catch {
    Write-Host "`n=== PROCESS STOPPED ===" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    Write-Host "No project files were deleted." -ForegroundColor Yellow
    exit 1
}
