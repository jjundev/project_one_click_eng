[CmdletBinding()]
param(
    [string]$LockPath = (Join-Path (Split-Path -Parent $PSScriptRoot) "LOCK.json"),
    [string]$FailureReason = "Not provided",
    [string]$ReproSteps = "Not provided",
    [string]$LogInfo = "Not provided",
    [string]$NextAction = "Re-open coding and create fix job."
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
# Run with: powershell -NoProfile -ExecutionPolicy Bypass -File <script>.ps1

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

function Normalize-Text([string]$Value, [string]$DefaultValue) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }

    return $Value.Trim()
}

if (-not (Test-Path $LockPath -PathType Leaf)) {
    Fail "LOCK.json not found: $LockPath"
}

$jobsRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

try {
    $lock = Get-Content $LockPath -Raw | ConvertFrom-Json -ErrorAction Stop
} catch {
    Fail "Invalid LOCK.json format: $($_.Exception.Message)"
}

if ($lock.stage -ne "qa") {
    Fail "LOCK stage is not qa: $($lock.stage). fail_job requires qa stage."
}

if (-not $lock.job_id) {
    Fail "LOCK.json must contain job_id."
}

$jobId = [string]$lock.job_id
$activeDir = Join-Path $jobsRoot ("active\$jobId")
$failedDir = Join-Path $jobsRoot ("failed\$jobId")
$failReportPath = Join-Path $failedDir "qa-fail.md"

if (-not (Test-Path $activeDir -PathType Container)) {
    Fail "Active job directory missing: $activeDir"
}
if (Test-Path $failedDir -PathType Container) {
    Fail "Failed directory already exists: $failedDir"
}

Move-Item -Path $activeDir -Destination $failedDir

$failureReasonSafe = Normalize-Text -Value $FailureReason -DefaultValue "Not provided"
$reproStepsSafe = Normalize-Text -Value $ReproSteps -DefaultValue "Not provided"
$logInfoSafe = Normalize-Text -Value $LogInfo -DefaultValue "Not provided"
$nextActionSafe = Normalize-Text -Value $NextAction -DefaultValue "Re-open coding and create fix job."

$failTemplate = @"
# QA Fail Report: $jobId

## Cause
- $failureReasonSafe

## Repro Steps
- $reproStepsSafe

## Log
- $logInfoSafe

## Next Action
- $nextActionSafe
"@

Set-Content -Path $failReportPath -Value $failTemplate -Encoding utf8
Remove-Item -Path $LockPath -Force

Write-Output "fail_job: job_id=$jobId, failed_dir=$failedDir, fail_report=$failReportPath"
Write-Output "fail_job: LOCK.json removed."
