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
echo %CD%^|mvn^|%*>>"%PHASE_GATE_INVOCATION_LOG%"
if "%~1"=="-version" (
  echo Apache Maven 3.9.9
  echo Java version: $JavaVersion, vendor: Test
)
if not "%PHASE_GATE_FAKE_FAIL_ARGUMENTS%"=="" if "%*"=="%PHASE_GATE_FAKE_FAIL_ARGUMENTS%" exit /b %PHASE_GATE_FAKE_EXIT_CODE%
exit /b 0
"@
    $pnpmCommand = @"
@echo off
echo %CD%^|pnpm^|%*>>"%PHASE_GATE_INVOCATION_LOG%"
if not "%PHASE_GATE_FAKE_FAIL_ARGUMENTS%"=="" if "%*"=="%PHASE_GATE_FAKE_FAIL_ARGUMENTS%" exit /b %PHASE_GATE_FAKE_EXIT_CODE%
exit /b 0
"@
    Set-Content -LiteralPath (Join-Path $Directory 'mvn.cmd') -Value $mavenCommand -Encoding ascii
    Set-Content -LiteralPath (Join-Path $Directory 'pnpm.cmd') -Value $pnpmCommand -Encoding ascii
}

<#
.SYNOPSIS
精確比對命令呼叫紀錄，避免只靠 gate exit code 造成假綠。
#>
function Assert-InvocationLog {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [string[]]$Expected
    )

    $actual = @(Get-Content -LiteralPath $Path)
    Assert-PhaseGateTest -Condition ($actual.Count -eq $Expected.Count) `
        -Message "命令數量不符。expected=$($Expected -join '; ') actual=$($actual -join '; ')"
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        Assert-PhaseGateTest -Condition ($actual[$index] -ceq $Expected[$index]) `
            -Message "第 $($index + 1) 個命令不符。expected=$($Expected[$index]) actual=$($actual[$index])"
    }
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
$invocationLog = Join-Path $tempRoot 'invocations.log'

try {
    $env:PHASE_GATE_INVOCATION_LOG = $invocationLog
    $env:PHASE_GATE_FAKE_FAIL_ARGUMENTS = ''
    $env:PHASE_GATE_FAKE_EXIT_CODE = '0'
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
    $expectedSuccessfulInvocations = @(
        "$repoRoot|mvn|-version",
        "$repoRoot|mvn|-pl backend test",
        "$repoRoot|pnpm|--dir frontend exec tsc --noEmit",
        "$repoRoot|pnpm|--dir frontend run build",
        "$repoRoot|pnpm|--dir frontend exec playwright test frontend/e2e/sp1-smoke.spec.ts"
    )
    Assert-InvocationLog -Path $invocationLog -Expected $expectedSuccessfulInvocations

    # Build 中途失敗須保留原始 exit code，且不得執行後續 Playwright。
    Remove-Item -LiteralPath $invocationLog -Force
    $env:PHASE_GATE_FAKE_FAIL_ARGUMENTS = '--dir frontend run build'
    $env:PHASE_GATE_FAKE_EXIT_CODE = '37'
    $failureOutput = & pwsh $gateScript -Phase V21 -E2ESpec $e2eSpec 2>&1 | Out-String
    $failureExitCode = $LASTEXITCODE
    Assert-PhaseGateTest -Condition ($failureExitCode -eq 37) `
        -Message "Build 失敗應保留 exit 37，actual=$failureExitCode output=$failureOutput"
    Assert-InvocationLog -Path $invocationLog -Expected $expectedSuccessfulInvocations[0..3]

    # Maven 回報 Java 8 時，必須在任何 build/test 命令之前明確拒絕。
    Remove-Item -LiteralPath $invocationLog -Force
    $env:PHASE_GATE_FAKE_FAIL_ARGUMENTS = ''
    $env:PHASE_GATE_FAKE_EXIT_CODE = '0'
    $java8Bin = Join-Path $tempRoot 'java8'
    New-FakePhaseGateCommands -Directory $java8Bin -JavaVersion '1.8.0_461'
    $env:Path = "$java8Bin;$originalPath"
    $javaOutput = & pwsh $gateScript -Phase V21 -E2ESpec $e2eSpec 2>&1 | Out-String
    $javaExitCode = $LASTEXITCODE
    Assert-PhaseGateTest -Condition ($javaExitCode -ne 0) -Message 'Maven 使用 Java 8 時 gate 應 fail-fast。'
    Assert-PhaseGateTest -Condition ($javaOutput -match 'Java 21') -Message "Java 版本錯誤訊息應明確要求 Java 21，output=$javaOutput"
    Assert-InvocationLog -Path $invocationLog -Expected @("$repoRoot|mvn|-version")

    # 僅載入函式本體，確認它本身也拒絕 V21–V27 以外的階段，不依賴 script 頂層參數驗證。
    $tokens = $null
    $parseErrors = $null
    $gateAst = [System.Management.Automation.Language.Parser]::ParseFile($gateScript, [ref]$tokens, [ref]$parseErrors)
    Assert-PhaseGateTest -Condition ($parseErrors.Count -eq 0) -Message "gate script 語法解析失敗：$parseErrors"
    $prefixFunction = $gateAst.Find({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'New-PhaseDataPrefix'
    }, $true)
    Assert-PhaseGateTest -Condition ($null -ne $prefixFunction) -Message '找不到 New-PhaseDataPrefix 函式。'
    Invoke-Expression $prefixFunction.Extent.Text
    $validPrefix = New-PhaseDataPrefix -Phase V27
    Assert-PhaseGateTest -Condition ($validPrefix -match '^E2E_V27_\d{17}_$') -Message "合法 prefix 格式錯誤：$validPrefix"
    $invalidPhaseRejected = $false
    try {
        New-PhaseDataPrefix -Phase V28 | Out-Null
    } catch {
        $invalidPhaseRejected = $true
    }
    Assert-PhaseGateTest -Condition $invalidPhaseRejected -Message 'New-PhaseDataPrefix 必須獨立拒絕 V28。'

    Write-Host 'verify-phase-gate focused tests passed'
} finally {
    $env:Path = $originalPath
    Remove-Item Env:PHASE_GATE_INVOCATION_LOG -ErrorAction SilentlyContinue
    Remove-Item Env:PHASE_GATE_FAKE_FAIL_ARGUMENTS -ErrorAction SilentlyContinue
    Remove-Item Env:PHASE_GATE_FAKE_EXIT_CODE -ErrorAction SilentlyContinue
    Set-Location $originalLocation
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
