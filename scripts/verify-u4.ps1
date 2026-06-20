#Requires -Version 7.0

<#
.SYNOPSIS
    Unit 4 安全防護與全域錯誤例外驗收腳本。
.DESCRIPTION
    此腳本用以驗收 Spring Security, JWT 與 RFC 7807 ProblemDetail。
    腳本會執行：
    1. 驗證未攜帶 Token 存取受保護 API 會傳回 401。
    2. 驗證使用錯誤密碼登入時，傳回 401 ProblemDetail 規格。
    3. 驗證正常登入成功並取得 Token。
    4. 驗證帶上 Bearer Token 後能正常讀取資料。
#>

param(
    [string]$BaseUrl = "http://127.0.0.1:18080/api"
)

# 遇到錯誤即停止執行
$ErrorActionPreference = "Stop"

# 封裝 HTTP 請求函數
# 函式級註解：負責呼叫 API 並傳回自訂 Response 物件，包含 StatusCode 與解析成 String 的 Content
function Invoke-CrmRawRequest {
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
        SkipHttpErrorCheck = $true # 避免 PowerShell 對於 4xx 錯誤直接拋出阻斷異常，以利我們讀取 Response 狀態碼
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }

    $response = Invoke-WebRequest @params
    
    # 動態判定 Response.Content 型態，因應不同 PowerShell 版本進行適當的 String 轉碼
    $raw = $response.Content
    if ($raw -is [System.Byte[]]) {
        $rawString = [System.Text.Encoding]::UTF8.GetString($raw)
    } else {
        $rawString = $raw
    }
    
    return [PSCustomObject]@{
        StatusCode = $response.StatusCode
        Content = $rawString
    }
}

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Unit 4 - Spring Security & JWT 驗收開始" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. 未攜帶 Token 呼叫保護 API
Write-Host "`n[步驟 1] 未攜帶 Token 呼叫保護 API /api/customers..." -ForegroundColor Yellow
$req1 = Invoke-CrmRawRequest -Method GET -Path "/customers"
Write-Host "回應狀態碼: $($req1.StatusCode) (預期為 401)" -ForegroundColor Gray
if ($req1.StatusCode -ne 401) {
    throw "安全性漏洞！未登入請求未被攔截拒絕 (狀態碼非 401)！"
}
Write-Host "拒絕存取成功！安全保護發揮作用。" -ForegroundColor Green

# 2. 測試登入失敗（錯誤密碼）與 RFC 7807 錯誤格式
Write-Host "`n[步驟 2] 使用錯誤的密碼進行登入測試..." -ForegroundColor Yellow
$req2 = Invoke-CrmRawRequest -Method POST -Path "/auth/login" -Body @{
    username = "sales@aurora.local"
    password = "wrongpassword"
}
Write-Host "回應狀態碼: $($req2.StatusCode) (預期為 401)" -ForegroundColor Gray
if ($req2.StatusCode -ne 401) {
    throw "登入校驗漏洞！錯誤密碼未被拒絕！"
}

# 驗證 RFC 7807 ProblemDetail 結構 (以正則匹配欄位，確保避開編碼轉換異常)
$rawJson = $req2.Content
Write-Host "驗證 RFC 7807 ProblemDetail 格式..." -ForegroundColor Gray
Write-Host "  - 原始內容: $rawJson" -ForegroundColor Gray

if ($rawJson -match '"title"\s*:' -and $rawJson -match '"detail"\s*:' -and $rawJson -match '"instance"\s*:') {
    Write-Host "全域例外處理器與 ProblemDetail 格式校驗成功！" -ForegroundColor Green
} else {
    throw "錯誤格式不符合 RFC 7807 ProblemDetail 規範！"
}

# 3. 正常登入取得 Token
Write-Host "`n[步驟 3] 執行正常登入取得 Token..." -ForegroundColor Yellow
$req3 = Invoke-CrmRawRequest -Method POST -Path "/auth/login" -Body @{
    username = "sales@aurora.local"
    password = "password123"
}
if ($req3.StatusCode -ne 200) {
    throw "正常登入失敗！"
}
$loginObj = $req3.Content | ConvertFrom-Json
$token = $loginObj.token
if (-not $token) {
    throw "登入未回傳 token！"
}
Write-Host "登入成功！取得 Token: $($token.Substring(0, 15))..." -ForegroundColor Green

# 4. 攜帶 Token 存取保護 API
Write-Host "`n[步驟 4] 攜帶 Bearer Token 重新存取 /api/customers..." -ForegroundColor Yellow
$req4 = Invoke-CrmRawRequest -Method GET -Path "/customers" -Token $token
Write-Host "回應狀態碼: $($req4.StatusCode) (預期為 200)" -ForegroundColor Gray
if ($req4.StatusCode -ne 200) {
    throw "攜帶正確 Token 存取失敗！"
}
$customersObj = $req4.Content | ConvertFrom-Json
Write-Host "資料讀取成功！共查詢到 $($customersObj.totalElements) 筆客戶資料。" -ForegroundColor Green

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host "  Unit 4 Spring Security & JWT 驗證順利通過！" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
