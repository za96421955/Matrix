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

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BinDir = $ScriptDir
$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$LogsDir = Join-Path $LocalDir "logs"
$ServicePidFile = Join-Path $BinDir "app.pid"
$WebuiPidFile = Join-Path $BinDir "webui.pid"

# ---- 日志函数 ----
function Write-Log {
    param([string]$Level = "INFO", [string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

# ---- 停止进程：优雅关闭 + 等待 + 强制终止 ----
function Stop-ProcessGracefully {
    param([int]$ProcessId, [string]$Label, [int]$WaitSeconds = 5)

    try {
        $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if (-not $proc) {
            return $false
        }
        Write-Log "INFO" "正在停止 $Label (PID: $ProcessId) ..."
        $proc.CloseMainWindow() | Out-Null

        # 逐秒等待进程退出
        for ($i = 0; $i -lt $WaitSeconds; $i++) {
            Start-Sleep -Seconds 1
            $check = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
            if (-not $check) {
                Write-Log "INFO" "$Label 已停止"
                return $true
            }
        }

        # 超时后强制终止
        $check = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if ($check) {
            Write-Log "WARN" "$Label 进程未退出，强制终止"
            Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 1
            Write-Log "INFO" "$Label 已停止"
        }
        return $true
    } catch {
        Write-Log "WARN" "停止 $Label 时出错: $_"
        try { Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue } catch { }
        return $true
    }
}

# ==========================================
# 主逻辑
# ==========================================

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service Stop (Windows)"
Write-Log "INFO" "=========================================="

# ============================================================
# 停止 WebUI 代理（三层查找）
# 对应 stop.sh 第 25-71 行
# ============================================================

$InstallBinDir = Join-Path $LocalDir "bin"
$InstallWebuiPidFile = Join-Path $InstallBinDir "webui.pid"
$InstallServicePidFile = Join-Path $InstallBinDir "app.pid"

$WebuiStopped = $false
$WebuiPid = $null
$WebuiPidFileFound = $WebuiPidFile

# 第一层：当前目录 PID 文件
if (Test-Path $WebuiPidFileFound) {
    $WebuiPid = Get-Content $WebuiPidFileFound -Raw | ForEach-Object { $_.Trim() }
}

# 第二层：安装目录 PID 文件 (~/.matrix/local/bin/webui.pid)
if (-not $WebuiPid -and (Test-Path $InstallWebuiPidFile)) {
    $WebuiPid = Get-Content $InstallWebuiPidFile -Raw | ForEach-Object { $_.Trim() }
    $WebuiPidFileFound = $InstallWebuiPidFile
}

# 第三层：通过进程名查找（匹配 proxy_server.py）
# ---- 修改点 1/2：进程名过滤增加 pythonw.exe ----
if (-not $WebuiPid) {
    try {
        $pyProcs = Get-CimInstance Win32_Process -Filter "Name='python.exe' OR Name='python3.exe' OR Name='pythonw.exe'" -ErrorAction SilentlyContinue
        foreach ($proc in $pyProcs) {
            if ($proc.CommandLine -match "proxy_server") {
                $WebuiPid = $proc.ProcessId
                Write-Log "INFO" "通过进程名匹配到 WebUI 代理 (PID=$WebuiPid)"
                break
            }
        }
        # 也检查 PowerShell 代理进程（保留兼容）
        if (-not $WebuiPid) {
            $psProcs = Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue
            foreach ($proc in $psProcs) {
                if ($proc.CommandLine -match "matrix-webui-proxy") {
                    $WebuiPid = $proc.ProcessId
                    Write-Log "INFO" "通过进程名匹配到 WebUI PowerShell 代理 (PID=$WebuiPid)"
                    break
                }
            }
        }
    } catch {
        try {
            # ---- 修改点 2/2：WMI 回退也增加 pythonw.exe ----
            $pyProcs = Get-WmiObject Win32_Process -Filter "Name='python.exe' OR Name='python3.exe' OR Name='pythonw.exe'" -ErrorAction SilentlyContinue
            foreach ($proc in $pyProcs) {
                if ($proc.CommandLine -match "proxy_server") {
                    $WebuiPid = $proc.ProcessId
                    Write-Log "INFO" "通过进程名匹配到 WebUI 代理 (PID=$WebuiPid)"
                    break
                }
            }
            if (-not $WebuiPid) {
                $psProcs = Get-WmiObject Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue
                foreach ($proc in $psProcs) {
                    if ($proc.CommandLine -match "matrix-webui-proxy") {
                        $WebuiPid = $proc.ProcessId
                        Write-Log "INFO" "通过进程名匹配到 WebUI PowerShell 代理 (PID=$WebuiPid)"
                        break
                    }
                }
            }
        } catch { }
    }
}

# 停止 WebUI（优雅关闭 + 5秒等待 + 强制终止）
if ($WebuiPid) {
    $WebuiStopped = Stop-ProcessGracefully -ProcessId $WebuiPid -Label "WebUI" -WaitSeconds 5
}

# 清理所有可能的 WebUI PID 文件
foreach ($pidFile in @($WebuiPidFile, $InstallWebuiPidFile)) {
    if (Test-Path $pidFile) {
        Remove-Item -Path $pidFile -Force -ErrorAction SilentlyContinue
    }
}

if (-not $WebuiStopped) {
    Write-Log "INFO" "WebUI 未在运行"
}

# ============================================================
# 停止后端 Java 服务（三层查找 + 进程名兜底）
# 对应 stop.sh 第 73-122 行
# ============================================================
Write-Log "INFO" "正在停止后端服务..."

$ServiceStopped = $false
$ServicePid = $null
$ServicePidFileFound = $ServicePidFile

# 第一层：当前目录 PID 文件
if (Test-Path $ServicePidFileFound) {
    $ServicePid = Get-Content $ServicePidFileFound -Raw | ForEach-Object { $_.Trim() }
}

# 第二层：安装目录 PID 文件
if (-not $ServicePid -and (Test-Path $InstallServicePidFile)) {
    $ServicePid = Get-Content $InstallServicePidFile -Raw | ForEach-Object { $_.Trim() }
    $ServicePidFileFound = $InstallServicePidFile
}

# 如果通过 PID 文件找到进程，进行优雅停机 + 强制兜底
if ($ServicePid) {
    $procExists = Get-Process -Id $ServicePid -ErrorAction SilentlyContinue
    if ($procExists) {
        $ServiceStopped = Stop-ProcessGracefully -ProcessId $ServicePid -Label "后端服务" -WaitSeconds 10
        # 清理所有可能的 PID 文件
        foreach ($pidFile in @($ServicePidFile, $InstallServicePidFile)) {
            if (Test-Path $pidFile) {
                Remove-Item -Path $pidFile -Force -ErrorAction SilentlyContinue
            }
        }
        if ($ServiceStopped) {
            Write-Log "INFO" "后端服务已停止 (PID: $ServicePid)"
        }
    } else {
        Write-Log "INFO" "PID 文件中的进程已不存在，清理文件"
        foreach ($pidFile in @($ServicePidFile, $InstallServicePidFile)) {
            if (Test-Path $pidFile) {
                Remove-Item -Path $pidFile -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

# 第三层：PID 文件失效时，通过进程名兜底清理（对应 stop.sh 第 104-120 行）
if (-not $ServiceStopped) {
    $foundPids = @()
    try {
        $javaProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
        foreach ($proc in $javaProcs) {
            if ($proc.CommandLine -match "matrix-local-.*\.jar") {
                $foundPids += $proc.ProcessId
            }
        }
    } catch {
        try {
            $javaProcs = Get-WmiObject Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
            foreach ($proc in $javaProcs) {
                if ($proc.CommandLine -match "matrix-local-.*\.jar") {
                    $foundPids += $proc.ProcessId
                }
            }
        } catch {
            Write-Log "WARN" "无法枚举进程: $_"
        }
    }

    if ($foundPids.Count -gt 0) {
        Write-Log "WARN" "通过进程名找到残留进程，正在清理..."
        foreach ($pid in $foundPids) {
            Stop-ProcessGracefully -ProcessId $pid -Label "后端服务(残留)" -WaitSeconds 5
            Write-Log "INFO" "已停止残留进程 PID: $pid"
        }
        $ServiceStopped = $true
    }
}

if (-not $ServiceStopped) {
    Write-Log "WARN" "未找到运行中的服务"
}

Start-Sleep -Seconds 1
Write-Log "INFO" "所有服务已停止"