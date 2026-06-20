#Requires -Version 7.0

<#
.SYNOPSIS
    一鍵啟動 AI CRM 智慧業務助理所有服務。
.DESCRIPTION
    此腳本將自動啟動 Docker 中的 PostgreSQL，並在獨立的 PowerShell 視窗中啟動後端 Spring Boot 伺服器與前端 Vite React 網頁。
#>

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  啟動 AI CRM 智慧業務助理系統" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. 啟動 PostgreSQL
Write-Host "`n[步驟 1] 啟動 PostgreSQL 容器 (Host Port 15432)..." -ForegroundColor Yellow
docker-compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker 容器啟動失敗，請確認 Docker Desktop 已開啟並正常運作。" -ForegroundColor Red
    exit 1
}
Write-Host "資料庫容器啟動成功！" -ForegroundColor Green

# 2. 啟動 Spring Boot 後端 (彈出新視窗以利觀看日誌)
Write-Host "`n[步驟 2] 啟動後端 Spring Boot 服務 (Port 18080)..." -ForegroundColor Yellow
$backendCmd = '$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend spring-boot:run'
Start-Process pwsh -ArgumentList "-NoExit", "-Command", "$backendCmd"

# 3. 啟動 React 前端 (彈出新視窗以利觀看日誌)
Write-Host "`n[步驟 3] 啟動前端 React 服務 (Port 5175)..." -ForegroundColor Yellow
$frontendCmd = '$env:Path = "D:\nodejs;$env:Path"; pnpm --dir frontend run dev -- --port 5175 --host 127.0.0.1'
Start-Process pwsh -ArgumentList "-NoExit", "-Command", "$frontendCmd"

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host "系統啟動指令已送出！已在新視窗獨立執行前後端服務。" -ForegroundColor Green
Write-Host "後端健康檢查: http://127.0.0.1:18080/api/health" -ForegroundColor Gray
Write-Host "前端工作網址: http://127.0.0.1:5175" -ForegroundColor Gray
Write-Host "課程教學網址: http://127.0.0.1:5173" -ForegroundColor Gray
Write-Host "=============================================" -ForegroundColor Green
