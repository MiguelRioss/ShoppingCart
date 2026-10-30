param(
    [switch]$Neon,
    [string]$EmailSenderRepo = (Join-Path $PSScriptRoot '..\..\EMAIL_SENDER')
)

$ErrorActionPreference = 'Stop'
$journeyRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$journeyPreviousRepo = $env:EMAIL_SENDER_REPO
$journeyPreviousMode = $env:JOURNEY_USE_NEON
Push-Location $journeyRoot
try {
    $env:EMAIL_SENDER_REPO = (Resolve-Path $EmailSenderRepo).Path
    $env:JOURNEY_USE_NEON = if ($Neon) { 'true' } else { 'false' }
    & .\gradlew.bat clientJourneyTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Client journey failed. See build/reports/tests/clientJourneyTest/index.html.' }
    Write-Host 'Client journey passed. No real payment or email was sent.'
} finally {
    if ($null -eq $journeyPreviousRepo) { Remove-Item Env:EMAIL_SENDER_REPO -ErrorAction SilentlyContinue } else { $env:EMAIL_SENDER_REPO = $journeyPreviousRepo }
    if ($null -eq $journeyPreviousMode) { Remove-Item Env:JOURNEY_USE_NEON -ErrorAction SilentlyContinue } else { $env:JOURNEY_USE_NEON = $journeyPreviousMode }
    Pop-Location
}
