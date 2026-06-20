#Requires -Version 7.0

<#
.SYNOPSIS
    Unit 3 資料庫與動態查詢驗收腳本。
    測試與真實 PostgreSQL 連線，並驗證 CustomerSpecification 的動態篩選功能。
.DESCRIPTION
    此腳本將會登入系統取得 JWT token，然後針對各種組合條件（名稱、產業、負責人）測試動態 SQL 查詢，並驗證結果是否符合預期。
#>

param(
    [string]$BaseUrl = "http://127.0.0.1:18080/api"
)

# 遇到錯誤即停止執行
$ErrorActionPreference = "Stop"

# 封裝 HTTP 請求函數
# 函式級註解：負責呼叫 CRM API 並處理 JSON 的轉換與 Header 攜帶
function Invoke-CrmRequest {
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
    
    return Invoke-RestMethod @params
}

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Unit 3 - PostgreSQL & Specification 驗收開始" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. 檢查後端健康狀態
Write-Host "`n[步驟 1] 檢查後端健康狀態..." -ForegroundColor Yellow
$health = Invoke-CrmRequest -Method GET -Path "/health"
Write-Host "後端狀態: $($health.status)" -ForegroundColor Green
Write-Host "系統時間: $($health.timestamp)" -ForegroundColor Green

# 2. 登入取得 Token
Write-Host "`n[步驟 2] 登入取得驗證 Token..." -ForegroundColor Yellow
$login = Invoke-CrmRequest -Method POST -Path "/auth/login" -Body @{
    username = "sales@aurora.local"
    password = "password123"
}
$token = $login.token
if (-not $token) {
    throw "無法取得 Token，登入失敗！"
}
Write-Host "登入成功！Token 取得完成。" -ForegroundColor Green

# 3. 測試多條件 Specification 動態搜尋
Write-Host "`n[步驟 3] 測試動態 Specification 查詢條件..." -ForegroundColor Yellow

# A. 模糊姓名搜尋
Write-Host "`n  -> 條件 A: 搜尋名稱包含 '星河' 的客戶" -ForegroundColor Cyan
$resultA = Invoke-CrmRequest -Method GET -Path "/customers?keyword=星河" -Token $token
Write-Host "     符合筆數: $($resultA.totalElements) 筆" -ForegroundColor Green
foreach ($item in $resultA.items) {
    Write-Host "     - 名稱: $($item.name), 產業: $($item.industry), 負責人: $($item.ownerName)" -ForegroundColor Gray
    if (-not $item.name.Contains("星河")) {
        throw "搜尋結果不符合模糊條件！"
    }
}

# B. 產業精確篩選
Write-Host "`n  -> 條件 B: 篩選產業為 '物流' 的客戶" -ForegroundColor Cyan
$resultB = Invoke-CrmRequest -Method GET -Path "/customers?industry=物流" -Token $token
Write-Host "     符合筆數: $($resultB.totalElements) 筆" -ForegroundColor Green
foreach ($item in $resultB.items) {
    Write-Host "     - 名稱: $($item.name), 產業: $($item.industry), 負責人: $($item.ownerName)" -ForegroundColor Gray
    if ($item.industry -ne "物流") {
        throw "搜尋結果產業不符！"
    }
}

# C. 負責人精確篩選
Write-Host "`n  -> 條件 C: 篩選負責人為 '陳柏翰' 的客戶" -ForegroundColor Cyan
$resultC = Invoke-CrmRequest -Method GET -Path "/customers?owner=陳柏翰" -Token $token
Write-Host "     符合筆數: $($resultC.totalElements) 筆" -ForegroundColor Green
foreach ($item in $resultC.items) {
    Write-Host "     - 名稱: $($item.name), 產業: $($item.industry), 負責人: $($item.ownerName)" -ForegroundColor Gray
    if ($item.ownerName -ne "陳柏翰") {
        throw "搜尋結果負責人不符！"
    }
}

# 4. 測試資料庫持久化
Write-Host "`n[步驟 4] 測試 PostgreSQL 資料持久化..." -ForegroundColor Yellow
$testName = "PG持久化測試客戶-" + (Get-Random)
Write-Host "建立新測試客戶: $testName" -ForegroundColor Cyan
$created = Invoke-CrmRequest -Method POST -Path "/customers" -Token $token -Body @{
    name = $testName
    email = "pgtest@example.com"
    phone = "0999888777"
    taxId = "11223344"
    industry = "雲端服務"
    ownerName = "林宜庭"
}

Write-Host "再次查詢確認資料是否存在於 PostgreSQL 中..." -ForegroundColor Cyan
$searchResult = Invoke-CrmRequest -Method GET -Path "/customers?keyword=$testName" -Token $token
if ($searchResult.totalElements -lt 1) {
    throw "無法查詢到剛建立的持久化客戶！"
}
Write-Host "持久化驗證成功！客戶已確認寫入實體 PostgreSQL 資料庫。" -ForegroundColor Green

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host "  Unit 3 PostgreSQL 與動態查詢驗證順利通過！" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
