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

if (-not (Test-Path -LiteralPath $E2ESpec -PathType Leaf)) {
    throw "E2E spec 不存在：$E2ESpec"
}

Write-Host "[$Phase] 執行後端測試"
Invoke-PhaseGateCommand -Command 'mvn' -Arguments @('-pl', 'backend', 'test')

Write-Host "[$Phase] 執行 TypeScript 型別檢查"
Invoke-PhaseGateCommand -Command 'pnpm' -Arguments @('--dir', 'frontend', 'exec', 'tsc', '--noEmit')

Write-Host "[$Phase] 建置前端"
Invoke-PhaseGateCommand -Command 'pnpm' -Arguments @('--dir', 'frontend', 'run', 'build')

Write-Host "[$Phase] 執行 E2E：$E2ESpec"
& pnpm --dir frontend exec playwright test $E2ESpec
exit $LASTEXITCODE
