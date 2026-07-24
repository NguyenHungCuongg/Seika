# Chaos Test: Circuit Breaker Verification
# Kịch bản này chạy k6 load test, sau đó chủ động tắt Wallet Service
# để kiểm chứng Circuit Breaker có hoạt động hay không.
#
# Cách dùng:
#   .\scripts\chaos-test.ps1

param(
    [string]$WalletContainer = "seika-wallet-service",
    [int]$WarmupSeconds = 15,
    [int]$ChaosSeconds = 30,
    [int]$RecoverySeconds = 20
)

$ErrorActionPreference = "Continue"

# Fix Unicode character display (e.g. checkmarks) in PowerShell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  CHAOS TEST: Circuit Breaker Verification" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# ------------------------------------------------------------------
# Phase 1: Start k6 load test in background
# ------------------------------------------------------------------
Write-Host "[Phase 1] Khoi dong K6 load test..." -ForegroundColor Green

$k6Job = Start-Job -ScriptBlock {
    param($ScriptDir)
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
    Set-Location $ScriptDir
    # Prefer local k6, fallback to Docker
    if (Get-Command k6 -ErrorAction SilentlyContinue) {
        k6 run --quiet scripts/load-test.js 2>&1
    } else {
        Get-Content scripts/load-test.js | docker run --rm -i -e BASE_URL="http://host.docker.internal:8080" grafana/k6 run --quiet - 2>&1
    }
} -ArgumentList (Get-Location).Path

Write-Host "  K6 dang chay o background (Job ID: $($k6Job.Id))" -ForegroundColor DarkGray

# ------------------------------------------------------------------
# Phase 2: Warm-up - let system stabilize
# ------------------------------------------------------------------
Write-Host ""
Write-Host "[Phase 2] Cho he thong warm-up $WarmupSeconds giay..." -ForegroundColor Yellow
for ($i = $WarmupSeconds; $i -gt 0; $i--) {
    Write-Host "`r  Con $i giay... " -NoNewline -ForegroundColor DarkGray
    Start-Sleep -Seconds 1
}
Write-Host ""

# ------------------------------------------------------------------
# Phase 3: CHAOS - Pause Wallet Service
# ------------------------------------------------------------------
Write-Host ""
Write-Host "[Phase 3] CHAOS INJECTION: Tam dung $WalletContainer!" -ForegroundColor Red
Write-Host "  Lenh: docker pause $WalletContainer" -ForegroundColor DarkGray

$pauseResult = docker pause $WalletContainer 2>&1
if ($LASTEXITCODE -ne 0) {
    # Try with compose project prefix
    $WalletContainer = "seika-wallet-service-1"
    Write-Host "  Thu lai voi ten: $WalletContainer" -ForegroundColor DarkGray
    $pauseResult = docker pause $WalletContainer 2>&1
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "  Wallet Service DA BI TAM DUNG!" -ForegroundColor Red
} else {
    Write-Host "  CANH BAO: Khong the tam dung Wallet Service." -ForegroundColor Yellow
    Write-Host "  $pauseResult" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "[Phase 3] Quan sat Circuit Breaker trong $ChaosSeconds giay..." -ForegroundColor Yellow
Write-Host "  Mo Grafana (http://localhost:3000) de xem bieu do!" -ForegroundColor DarkGray
for ($i = $ChaosSeconds; $i -gt 0; $i--) {
    Write-Host "`r  Con $i giay... " -NoNewline -ForegroundColor DarkGray
    Start-Sleep -Seconds 1
}
Write-Host ""

# ------------------------------------------------------------------
# Phase 4: Recovery - Unpause Wallet Service
# ------------------------------------------------------------------
Write-Host ""
Write-Host "[Phase 4] PHUC HOI: Khoi dong lai $WalletContainer!" -ForegroundColor Green
docker unpause $WalletContainer 2>&1 | Out-Null

Write-Host "  Wallet Service DA PHUC HOI!" -ForegroundColor Green
Write-Host "  Doi $RecoverySeconds giay de Circuit Breaker chuyen HALF-OPEN -> CLOSED..." -ForegroundColor DarkGray
for ($i = $RecoverySeconds; $i -gt 0; $i--) {
    Write-Host "`r  Con $i giay... " -NoNewline -ForegroundColor DarkGray
    Start-Sleep -Seconds 1
}
Write-Host ""

# ------------------------------------------------------------------
# Phase 5: Wait for k6 to finish and show results
# ------------------------------------------------------------------
Write-Host ""
Write-Host "[Phase 5] Doi K6 ket thuc..." -ForegroundColor Cyan
Wait-Job -Job $k6Job -Timeout 120 | Out-Null
$k6Output = Receive-Job -Job $k6Job
Remove-Job -Job $k6Job -Force 2>$null

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  KET QUA K6" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
$k6Output | ForEach-Object { Write-Host $_ }

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  HUONG DAN DOC KET QUA" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  1. rate_limited........: Nen > 0% (chung to Rate Limit hoat dong)" -ForegroundColor White
Write-Host "  2. rate_limit_429_total: So request bi chan boi Rate Limiter" -ForegroundColor White
Write-Host "  3. http_req_duration...: Trong Phase 3 (Chaos), neu p95 < 5s" -ForegroundColor White
Write-Host "     thi Circuit Breaker DA hoat dong (fallback tra ve ngay)" -ForegroundColor White
Write-Host "  4. errors..............: Ty le loi chap nhan < 10%" -ForegroundColor White
Write-Host ""
Write-Host "  Mo Grafana -> Explore -> Tempo de xem Trace Waterfall!" -ForegroundColor Yellow
Write-Host ""
