$ErrorActionPreference = 'Stop'

<#
.SYNOPSIS
建立 phase gate 自測使用的受控假命令。

.PARAMETER Directory
假命令輸出目錄。

.PARAMETER JavaVersion
`mvn -version` 應回報的 Java 版本。
#>
function New-FakePhaseGateCommands {
    param(
        [Parameter(Mandatory)]
        [string]$Directory,

        [Parameter(Mandatory)]
        [string]$JavaVersion
    )

    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    $mavenCommand = @"
@echo off
if "%~1"=="-version" (
  echo Apache Maven 3.9.9
  echo Java version: $JavaVersion, vendor: Test
)
exit /b 0
"@
    Set-Content -LiteralPath (Join-Path $Directory 'mvn.cmd') -Value $mavenCommand -Encoding ascii
    Set-Content -LiteralPath (Join-Path $Directory 'pnpm.cmd') -Value "@echo off`r`nexit /b 0`r`n" -Encoding ascii
}

<#
.SYNOPSIS
確認布林條件成立，否則中止 focused 自測。
#>
function Assert-PhaseGateTest {
    param(
        [Parameter(Mandatory)]
        [bool]$Condition,

        [Parameter(Mandatory)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

# 自測固定由 script 位置推導 repo，不依呼叫者 cwd。
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gateScript = Join-Path $repoRoot 'scripts\verify-phase-gate.ps1'
$e2eSpec = 'frontend/e2e/sp1-smoke.spec.ts'
$originalPath = $env:Path
$originalLocation = Get-Location
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "ai-crm-phase-gate-$([Guid]::NewGuid())"

try {
    $java21Bin = Join-Path $tempRoot 'java21'
    New-FakePhaseGateCommands -Directory $java21Bin -JavaVersion '21.0.8'
    $env:Path = "$java21Bin;$originalPath"

    # 從 repo 子目錄執行，spec 仍以 repo root 為基準解析，且 fake commands 全部成功。
    Push-Location (Join-Path $repoRoot 'frontend')
    try {
        $cwdOutput = & pwsh $gateScript -Phase V21 -E2ESpec $e2eSpec 2>&1 | Out-String
        $cwdExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    Assert-PhaseGateTest -Condition ($cwdExitCode -eq 0) -Message "子目錄執行應成功，exit=$cwdExitCode，output=$cwdOutput"

    # Maven 回報 Java 8 時，必須在任何 build/test 命令之前明確拒絕。
    $java8Bin = Join-Path $tempRoot 'java8'
    New-FakePhaseGateCommands -Directory $java8Bin -JavaVersion '1.8.0_461'
    $env:Path = "$java8Bin;$originalPath"
    $javaOutput = & pwsh $gateScript -Phase V21 -E2ESpec $e2eSpec 2>&1 | Out-String
    $javaExitCode = $LASTEXITCODE
    Assert-PhaseGateTest -Condition ($javaExitCode -ne 0) -Message 'Maven 使用 Java 8 時 gate 應 fail-fast。'
    Assert-PhaseGateTest -Condition ($javaOutput -match 'Java 21') -Message "Java 版本錯誤訊息應明確要求 Java 21，output=$javaOutput"

    Write-Host 'verify-phase-gate focused tests passed'
} finally {
    $env:Path = $originalPath
    Set-Location $originalLocation
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
