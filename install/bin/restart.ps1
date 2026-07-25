<#
.SYNOPSIS
    Matrix 本地服务重启脚本 (Windows)
.DESCRIPTION
    先停止所有服务，再重新启动
.NOTES
    对应 restart.sh 的 PowerShell 实现
    版本: 1.0.3.alpha
#>

# ==========================================
# 初始化
# ==========================================

$OutputEncoding = [Text.Encoding]::UTF8
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ---- 日志函数 ----
function Write-Log {
    param([string]$Level = "INFO", [string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

# ==========================================
# 主逻辑
# ==========================================

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service Restart (Windows)"
Write-Log "INFO" "=========================================="

# 1. 停止服务
$stopScript = Join-Path $ScriptDir "stop.ps1"
if (-not (Test-Path $stopScript)) {
    Write-Log "ERROR" "未找到 stop.ps1: $stopScript"
    pause
    exit 1
}

Write-Log "INFO" "正在停止服务..."
& $stopScript

# 2. 等待
Write-Log "INFO" "等待 2 秒后启动..."
Start-Sleep -Seconds 2

# 3. 启动服务
$startScript = Join-Path $ScriptDir "start.ps1"
if (-not (Test-Path $startScript)) {
    Write-Log "ERROR" "未找到 start.ps1: $startScript"
    pause
    exit 1
}

Write-Log "INFO" "正在启动服务..."
& $startScript

Write-Log "INFO" "重启完成"
