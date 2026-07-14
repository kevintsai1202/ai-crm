param(
    [Parameter(Mandatory)]
    [ValidateSet('V21', 'V22', 'V23', 'V24', 'V25', 'V26', 'V27')]
    [string]$Phase,

    [Parameter(Mandatory)]
    [string]$E2ESpec
)

$ErrorActionPreference = 'Stop'

<#
.SYNOPSIS
建立可隔離單一階段 E2E 測試資料的前綴。

.PARAMETER Phase
階段代號，例如 V21。

.OUTPUTS
格式為 E2E_<PHASE>_<UTC timestamp>_ 的字串。
#>
function New-PhaseDataPrefix {
    param(
        [Parameter(Mandatory)]
        [string]$Phase
    )

    # 使用毫秒精度的 UTC 時間戳，降低連續執行不同測試時發生名稱碰撞的機率。
    $utcTimestamp = [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')
    return "E2E_$($Phase.ToUpperInvariant())_${utcTimestamp}_"
}

<#
.SYNOPSIS
執行外部驗證命令，並在失敗時保留原始 exit code 終止 gate。

.PARAMETER Command
要執行的命令名稱。

.PARAMETER Arguments
傳給命令的參數陣列。
#>
function Invoke-PhaseGateCommand {
    param(
        [Parameter(Mandatory)]
        [string]$Command,

        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

<#
.SYNOPSIS
確認 Maven 實際使用的 Java major version 為 21。

.DESCRIPTION
以 `mvn -version` 為準，而非只讀 JAVA_HOME，避免 PATH 與 JAVA_HOME 指向不同 JDK。
#>
function Assert-MavenUsesJava21 {
    $mavenVersionOutput = & mvn -version 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "無法執行 mvn -version，請確認 Maven 已加入 PATH。`n$mavenVersionOutput"
    }

    # Maven 標準輸出含「Java version: <版本>」；Java 8 的 1.8 格式不可誤判為 major 1。
    $javaVersionMatch = [regex]::Match($mavenVersionOutput, '(?im)^Java version:\s*(?<version>[^,\s]+)')
    if (-not $javaVersionMatch.Success) {
        throw "無法從 mvn -version 判斷 Java 版本；本專案要求 Maven 使用 Java 21。`n$mavenVersionOutput"
    }

    $javaVersion = $javaVersionMatch.Groups['version'].Value
    $javaMajor = if ($javaVersion.StartsWith('1.')) {
        [int]($javaVersion.Split('.')[1])
    } else {
        [int]($javaVersion.Split('.')[0])
    }
    if ($javaMajor -ne 21) {
        throw "Maven 必須使用 Java 21，目前偵測到 Java $javaVersion。請調整 JAVA_HOME 與 PATH 後重試。"
    }
}

# script 位於 <repo>/scripts，所有相對路徑一律以 repo root 為準，不受呼叫者 cwd 影響。
$repoRoot = Split-Path -Parent $PSScriptRoot
$candidateE2ESpec = if ([System.IO.Path]::IsPathRooted($E2ESpec)) {
    $E2ESpec
} else {
    Join-Path $repoRoot $E2ESpec
}
if (-not (Test-Path -LiteralPath $candidateE2ESpec -PathType Leaf)) {
    throw "E2E spec 不存在：$E2ESpec"
}
$resolvedE2ESpec = (Resolve-Path -LiteralPath $candidateE2ESpec).Path
# Playwright 在 Windows 會把絕對路徑當成 test regex 而找不到測試，執行時改傳 repo-relative path。
$repoRelativeE2ESpec = [System.IO.Path]::GetRelativePath($repoRoot, $resolvedE2ESpec).Replace('\', '/')
$locationPushed = $false

try {
    Push-Location -LiteralPath $repoRoot
    $locationPushed = $true
    Assert-MavenUsesJava21

    Write-Host "[$Phase] 執行後端測試"
    Invoke-PhaseGateCommand -Command 'mvn' -Arguments @('-pl', 'backend', 'test')

    Write-Host "[$Phase] 執行 TypeScript 型別檢查"
    Invoke-PhaseGateCommand -Command 'pnpm' -Arguments @('--dir', 'frontend', 'exec', 'tsc', '--noEmit')

    Write-Host "[$Phase] 建置前端"
    Invoke-PhaseGateCommand -Command 'pnpm' -Arguments @('--dir', 'frontend', 'run', 'build')

    Write-Host "[$Phase] 執行 E2E：$repoRelativeE2ESpec"
    & pnpm --dir frontend exec playwright test $repoRelativeE2ESpec
    exit $LASTEXITCODE
} finally {
    if ($locationPushed) {
        Pop-Location
    }
}
