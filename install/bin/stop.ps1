<#
.SYNOPSIS
    Matrix 本地服务停止脚本 (Windows)
.DESCRIPTION
    停止 WebUI 代理和 Matrix 后端 Java 服务
.NOTES
    对应 stop.sh 的 PowerShell 实现
    版本: 1.0.2
#>

# ==========================================
# 初始化
# ==========================================

$OutputEncoding = [Text.Encoding]::UTF8
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$PidDir = Join-Path $LocalDir "tmp"
$LogsDir = Join-Path $LocalDir "logs"
$ServicePidFile = Join-Path $PidDir "matrix-local.pid"
$WebuiPidFile = Join-Path $PidDir "matrix-webui.pid"

# ---- 日志函数 ----
function Write-Log {
    param([string]$Level = "INFO", [string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

# ---- 通过 PID 文件停止进程 ----
function Stop-ProcessByPidFile {
    param([string]$PidFile, [string]$ProcessLabel)

    if (-not (Test-Path $PidFile)) {
        Write-Log "INFO" "$ProcessLabel 未运行 (无 PID 文件)"
        return
    }

    $pid = Get-Content $PidFile -Raw | ForEach-Object { $_.Trim() }
    if (-not $pid) {
        Write-Log "WARN" "$ProcessLabel PID 文件为空"
        Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
        return
    }

    Write-Log "INFO" "正在停止 $ProcessLabel (PID=$pid)..."

    try {
        $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($proc) {
            $proc.CloseMainWindow() | Out-Null
            Start-Sleep -Seconds 2

            $procCheck = Get-Process -Id $pid -ErrorAction SilentlyContinue
            if ($procCheck) {
                Write-Log "WARN" "$ProcessLabel 未响应，强制终止..."
                Stop-Process -Id $pid -Force
                Start-Sleep -Seconds 1
            }
        }
    } catch {
        Write-Log "WARN" "停止 $ProcessLabel 时出错: $_"
        try {
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        } catch { }
    }

    Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
    Write-Log "INFO" "$ProcessLabel 已停止"
}

# ---- 通过命令行匹配停止进程 ----
function Stop-ProcessByCommandLine {
    param([string]$Pattern, [string]$ProcessLabel, [string]$ProcessName)

    try {
        $filter = "Name='java.exe'"
        if ($ProcessName) {
            $filter = "Name='$ProcessName'"
        } else {
            $filter = "Name='java.exe' OR Name='python.exe' OR Name='python3.exe' OR Name='powershell.exe'"
        }

        $procs = Get-CimInstance Win32_Process -Filter $filter -ErrorAction SilentlyContinue
        foreach ($proc in $procs) {
            $cmdLine = $proc.CommandLine
            if ($cmdLine -and $cmdLine -match $Pattern) {
                Write-Log "INFO" "通过命令行匹配到 $ProcessLabel 进程 (PID=$($proc.ProcessId))"
                try {
                    $p = Get-Process -Id $proc.ProcessId -ErrorAction SilentlyContinue
                    if ($p) {
                        $p.CloseMainWindow() | Out-Null
                        Start-Sleep -Seconds 1
                    }
                    Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
                    Write-Log "INFO" "$ProcessLabel (PID=$($proc.ProcessId)) 已停止"
                } catch {
                    Write-Log "WARN" "停止 $ProcessLabel (PID=$($proc.ProcessId)) 失败: $_"
                }
            }
        }
    } catch {
        try {
            $procs = Get-WmiObject Win32_Process -Filter $filter -ErrorAction SilentlyContinue
            foreach ($proc in $procs) {
                $cmdLine = $proc.CommandLine
                if ($cmdLine -and $cmdLine -match $Pattern) {
                    Write-Log "INFO" "通过命令行匹配到 $ProcessLabel 进程 (PID=$($proc.ProcessId))"
                    Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
                    Write-Log "INFO" "$ProcessLabel (PID=$($proc.ProcessId)) 已停止"
                }
            }
        } catch {
            Write-Log "WARN" "无法枚举进程: $_"
        }
    }
}

# ==========================================
# 主逻辑
# ==========================================

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service Stop (Windows)"
Write-Log "INFO" "=========================================="

# 1. 停止 WebUI 代理（从 PID 文件）
Stop-ProcessByPidFile -PidFile $WebuiPidFile -ProcessLabel "WebUI 代理"

# 2. 停止后端服务（从 PID 文件）
Stop-ProcessByPidFile -PidFile $ServicePidFile -ProcessLabel "后端服务"

# 3. 通过命令行匹配残留进程
Write-Log "INFO" "检查残留进程..."
Stop-ProcessByCommandLine -Pattern "matrix-local" -ProcessLabel "后端服务(残留)" -ProcessName "java.exe"
Stop-ProcessByCommandLine -Pattern "proxy_server" -ProcessLabel "WebUI Python 代理(残留)" -ProcessName $null
Stop-ProcessByCommandLine -Pattern "matrix-webui-proxy" -ProcessLabel "WebUI PowerShell 代理(残留)" -ProcessName "powershell.exe"

# 4. 清理旧的 WebUI PID 文件（如果残留）
if (Test-Path (Join-Path $PidDir "matrix-webui-proxy.ps1")) {
    Remove-Item -Path (Join-Path $PidDir "matrix-webui-proxy.ps1") -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 1
Write-Log "INFO" "所有服务已停止"
