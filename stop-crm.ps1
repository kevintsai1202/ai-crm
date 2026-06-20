#Requires -Version 7.0

<#
.SYNOPSIS
    一鍵停止 AI CRM 智慧業務助理所有服務。
.DESCRIPTION
    此腳本將終止本機運行的 Java 與 Node/Vite 開發伺服器進程，並關閉 Docker 中的 PostgreSQL 資料庫。
#>

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  停止 AI CRM 智慧業務助理系統" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. 停止 Java 與 Node 進程
Write-Host "`n[步驟 1] 關閉後端與前端運行進程..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
foreach ($p in $javaProcesses) {
    # 關閉正在執行專案的 Java 後端
    Stop-Process -Id $p.Id -Force
    Write-Host "已關閉 Java 進程 (PID: $($p.Id))" -ForegroundColor Gray
}

$nodeProcesses = Get-Process -Name "node" -ErrorAction SilentlyContinue
foreach ($p in $nodeProcesses) {
    # 關閉 Node/Vite 伺服器
    Stop-Process -Id $p.Id -Force
    Write-Host "已關閉 Node 進程 (PID: $($p.Id))" -ForegroundColor Gray
}

# 2. 關閉 PostgreSQL 容器
Write-Host "`n[步驟 2] 關閉 PostgreSQL 容器..." -ForegroundColor Yellow
docker-compose down
Write-Host "資料庫容器已停止並移除。" -ForegroundColor Green

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host "  所有 AI CRM 服務已成功停止！" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
