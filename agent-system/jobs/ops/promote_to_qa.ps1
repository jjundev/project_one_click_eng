[CmdletBinding()]
param(
    [string]$LockPath = (Join-Path (Split-Path -Parent $PSScriptRoot) "LOCK.json")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
# Run with: powershell -NoProfile -ExecutionPolicy Bypass -File <script>.ps1

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
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

if (-not $lock.job_id) {
    Fail "LOCK.json must contain job_id."
}

$jobId = [string]$lock.job_id
$activeDir = Join-Path $jobsRoot ("active\$jobId")
$reportPath = Join-Path $jobsRoot ("reports\$($jobId)-report.md")

if ($lock.stage -ne "coding") {
    Fail "LOCK stage is not coding: $($lock.stage). promotion is blocked."
}
if (-not (Test-Path $activeDir -PathType Container)) {
    Fail "Active job directory missing: $activeDir"
}
if (-not (Test-Path $reportPath -PathType Leaf)) {
    Fail "Report file missing: $reportPath"
}

function Get-BuildResult([string]$Path) {
    $lines = Get-Content $Path -ErrorAction Stop
    $inBuildResult = $false
    $resultValue = $null

    foreach ($line in $lines) {
        if ($line -match '^\s*##\s*Build Result\s*$') {
            $inBuildResult = $true
            continue
        }

        if ($inBuildResult -and $line -match '^\s*##\s+') {
            break
        }

        if ($inBuildResult -and $line -match '^\s*-\s*Result\s*:\s*([^\r\n]+)\s*$') {
            $resultValue = $matches[1].Trim()
            break
        }
    }

    return @{
        FoundSection = $inBuildResult
        Result      = $resultValue
    }
}

$buildMeta = Get-BuildResult -Path $reportPath
if (-not $buildMeta.FoundSection) {
    Fail "Build Result section not found in report: $reportPath"
}
if (-not $buildMeta.Result) {
    Fail "Build Result is missing in report section. Expected '- Result: PASS': $reportPath"
}
if ($buildMeta.Result -ne "PASS") {
    Fail "Build Result must be PASS. Found: $($buildMeta.Result)"
}

$lock.stage = "qa"
$lockContent = $lock | ConvertTo-Json -Depth 10
Set-Content -Path $LockPath -Value $lockContent -Encoding utf8

Write-Output "promote_to_qa: job=$jobId, stage=qa"
