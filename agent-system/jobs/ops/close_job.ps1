[CmdletBinding()]
param(
    [string]$LockPath = (Join-Path (Split-Path -Parent $PSScriptRoot) "LOCK.json"),
    [switch]$UserPass,
    [ValidateSet("PASS")]
    [string]$MergeStatus = "PASS",
    [ValidateSet("PASS")]
    [string]$SmokeStatus = "PASS",
    [string]$MergeEvidence = "",
    [string]$SmokeEvidence = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
# Run with: powershell -NoProfile -ExecutionPolicy Bypass -File <script>.ps1 -UserPass

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

if (-not $UserPass.IsPresent) {
    Fail "User PASS must be explicitly passed via -UserPass for close_job."
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
    Fail "LOCK stage is not qa: $($lock.stage). close_job requires qa stage."
}

if (-not $lock.job_id) {
    Fail "LOCK.json must contain job_id."
}

$jobId = [string]$lock.job_id
$activeDir = Join-Path $jobsRoot ("active\$jobId")
$doneDir = Join-Path $jobsRoot ("done\$jobId")
$reviewDir = Join-Path $jobsRoot "review"
$finalReportPath = Join-Path $reviewDir "$jobId-final.md"
$reportPath = Join-Path $jobsRoot ("reports\$($jobId)-report.md")

if ($MergeStatus -ne "PASS") {
    Fail "MergeStatus must be PASS. close_job is only for PASS flow."
}
if ($SmokeStatus -ne "PASS") {
    Fail "SmokeStatus must be PASS. close_job is only for PASS flow."
}
if (-not (Test-Path $activeDir -PathType Container)) {
    Fail "Active job directory missing: $activeDir"
}
if (Test-Path $doneDir -PathType Container) {
    Fail "Done directory already exists: $doneDir"
}
if (-not (Test-Path $reportPath -PathType Leaf)) {
    Fail "Coding report missing: $reportPath"
}
if (-not (Test-Path $reviewDir -PathType Container)) {
    New-Item -ItemType Directory -Path $reviewDir -Force | Out-Null
}
if (Test-Path $finalReportPath -PathType Leaf) {
    Fail "Final review file already exists: $finalReportPath"
}

$reviewTemplate = @"
# Final Review Report: $jobId

## Final Decision
- PASS

## Merge
- Status: $MergeStatus
- Evidence: $MergeEvidence

## Post-merge Smoke
- Status: $SmokeStatus
- Evidence: $SmokeEvidence

## Artifacts
- `jobs/reports/$jobId-report.md`
- `jobs/done/$jobId/job.md`
- `jobs/done/$jobId/<modified files>`

## Risks / Next Action
- Risks:
  - (선택 입력)
- Next Action:
  - (선택 입력)
"@

$reviewText = $reviewTemplate
$reviewText = $reviewTemplate
Set-Content -Path $finalReportPath -Value $reviewText -Encoding utf8

Move-Item -Path $activeDir -Destination $doneDir
$lock.stage = "done"
$lockContent = $lock | ConvertTo-Json -Depth 10
Set-Content -Path $LockPath -Value $lockContent -Encoding utf8
Remove-Item -Path $LockPath -Force

Write-Output "close_job: job_id=$jobId, done_dir=$doneDir, final_report=$finalReportPath"
Write-Output "close_job: stage moved to done and lock removed."
