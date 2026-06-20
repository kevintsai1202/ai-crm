#Requires -Version 7.0

<#
.SYNOPSIS
檢查 AI CRM 教學專案所需的 Windows 開發環境。
#>

$ErrorActionPreference = "Continue"

function Write-Ok {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Test-Command {
    param([string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-Java21 {
    $javaCandidates = @()
    if ($env:JAVA_HOME) {
        $javaCandidates += Join-Path $env:JAVA_HOME "bin\java.exe"
    }
    $javaCandidates += "D:\java\jdk-21\bin\java.exe"
    if (Test-Command "java") {
        $javaCandidates += "java"
    }

    foreach ($java in $javaCandidates | Select-Object -Unique) {
        try {
            $versionText = & $java -version 2>&1 | Out-String
            if ($versionText -match 'version "21|openjdk version "21') {
                Write-Ok "Java 21 可用：$java"
                return $true
            }
        } catch {
            continue
        }
    }

    Write-Fail "找不到 Java 21。請安裝 JDK 21，或設定 `$env:JAVA_HOME = 'D:\java\jdk-21'。"
    return $false
}

function Test-Maven {
    if (-not (Test-Command "mvn")) {
        Write-Fail "找不到 Maven。可使用 winget install Apache.Maven 或安裝 Maven 3.9+。"
        return $false
    }
    $version = mvn -version 2>&1 | Out-String
    if ($version -match "Apache Maven") {
        Write-Ok "Maven 可用。"
        return $true
    }
    Write-Fail "Maven 無法正常執行。"
    return $false
}

function Test-Node {
    if (-not (Test-Command "node")) {
        Write-Fail "找不到 node.exe；目前 pnpm 可能可用但 npm/node PATH 不完整。請安裝 Node.js 18+ 或修正 PATH。"
        return $false
    }
    $nodeVersion = node -v
    if ($nodeVersion -match '^v(1[8-9]|[2-9][0-9])\.') {
        Write-Ok "Node.js 可用：$nodeVersion"
        return $true
    }
    Write-Fail "Node.js 版本需為 v18+，目前為 $nodeVersion。"
    return $false
}

function Test-Docker {
    if (-not (Test-Command "docker")) {
        Write-Fail "找不到 Docker。請安裝 Docker Desktop。"
        return $false
    }
    docker info *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "Docker daemon 可用。"
        return $true
    }
    Write-Fail "Docker 已安裝但 daemon 未啟動。請啟動 Docker Desktop。"
    return $false
}

$results = @(
    Test-Java21
    Test-Maven
    Test-Node
    Test-Docker
)

if ($results -contains $false) {
    Write-Host "`n環境檢查未全部通過；紅燈項目請先修正。" -ForegroundColor Red
    exit 1
}

Write-Host "`n環境檢查全部通過。" -ForegroundColor Green

