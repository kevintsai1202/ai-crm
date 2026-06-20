#Requires -Version 7.0

<#
.SYNOPSIS
驗證 Unit 2 到 Unit 8 的主要後端 API 流程。
#>

param(
    [string]$BaseUrl = "http://127.0.0.1:18080/api"
)

$ErrorActionPreference = "Stop"

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null
    )

    $headers = @{}
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }
    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }
    Invoke-RestMethod @params
}

Write-Host "1. Health check" -ForegroundColor Cyan
$health = Invoke-Json -Method GET -Path "/health"
if ($health.status -ne "UP") { throw "Health check failed" }

Write-Host "2. Login" -ForegroundColor Cyan
$login = Invoke-Json -Method POST -Path "/auth/login" -Body @{
    username = "sales@aurora.local"
    password = "password123"
}
$token = $login.token
if (-not $token) { throw "Login did not return token" }

Write-Host "3. Query customers" -ForegroundColor Cyan
$customers = Invoke-Json -Method GET -Path "/customers?page=0&size=10" -Token $token
if ($customers.items.Count -lt 3) { throw "Seed customers missing" }

Write-Host "4. Validation failure should return 400" -ForegroundColor Cyan
try {
    Invoke-Json -Method POST -Path "/customers" -Token $token -Body @{
        name = "錯誤資料"
        email = "not-email"
        phone = "123"
        taxId = "abc"
        industry = "測試"
        ownerName = "業務"
    } | Out-Null
    throw "Validation request unexpectedly succeeded"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 400) {
        throw
    }
}

Write-Host "5. Create customer" -ForegroundColor Cyan
$created = Invoke-Json -Method POST -Path "/customers" -Token $token -Body @{
    name = "新客戶股份有限公司"
    email = "contact@example.com"
    phone = "0912345678"
    taxId = "87654321"
    industry = "SaaS"
    ownerName = "業務代表"
    contractStartDate = "2026-01-01"
    contractEndDate = "2026-12-31"
    renewalDueDate = "2026-11-30"
}

Write-Host "6. Update status and add interaction" -ForegroundColor Cyan
Invoke-Json -Method PUT -Path "/customers/$($created.id)/status" -Token $token -Body @{ status = "ACTIVE" } | Out-Null
Invoke-Json -Method POST -Path "/customers/$($created.id)/interactions" -Token $token -Body @{
    type = "EMAIL"
    occurredAt = "2026-06-13T10:00:00"
    content = "客戶要求安排產品續約簡報。"
} | Out-Null

Write-Host "7. AI chat and Agent trace" -ForegroundColor Cyan
$chat = Invoke-Json -Method POST -Path "/ai/chat" -Token $token -Body @{
    customerId = 1
    message = "請分析近期風險"
}
if (-not $chat.answer.Contains("根據 CRM 資料庫")) { throw "AI answer did not cite CRM data" }

$trace = Invoke-Json -Method GET -Path "/agent/customers/1/trace" -Token $token
if ($trace.steps.Count -lt 3) { throw "Agent trace too short" }

Write-Host "後端 API 驗證通過。" -ForegroundColor Green
