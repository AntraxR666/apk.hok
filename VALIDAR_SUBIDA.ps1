$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "SUBIR_A_APK_HOK.ps1"
if (-not (Test-Path -LiteralPath $scriptPath)) { throw "Missing SUBIR_A_APK_HOK.ps1" }
$text = Get-Content -LiteralPath $scriptPath -Raw
if ($text -match [regex]::Escape("(& gh api user --jq '.email // empty').Trim()")) {
    throw "Unsafe nullable email Trim detected"
}
if ($text -notmatch [regex]::Escape('[string]::IsNullOrWhiteSpace($email)')) {
    throw "Missing null-safe email fallback"
}
if ($text -notmatch [regex]::Escape('gh auth login --hostname github.com --git-protocol https --web')) {
    throw "Missing browser login fallback"
}
Write-Host "UPLOAD_SCRIPT_VALIDATION_OK" -ForegroundColor Green
