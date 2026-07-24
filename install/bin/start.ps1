<#
.SYNOPSIS
    Matrix 本地服务启动脚本 (Windows)
.DESCRIPTION
    检测 JDK 21，启动 Matrix 后端 Java 服务，启动 WebUI 代理服务
.NOTES
    对应 start.sh 的 PowerShell 实现
    版本: 1.0.2
#>

$OutputEncoding = [Text.Encoding]::UTF8
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$BinDir = Join-Path $LocalDir "bin"
$JdksDir = Join-Path $MatrixHome "jdk21"
$LogsDir = Join-Path $LocalDir "logs"
$ConfigDir = Join-Path $LocalDir "config"

function Write-Log {
    param([string]$Level = "INFO", [string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

$ServicePidFile = Join-Path $BinDir "app.pid"
$WebuiPidFile = Join-Path $BinDir "webui.pid"

if (-not (Test-Path $BinDir)) {
    New-Item -ItemType Directory -Path $BinDir -Force | Out-Null
}

# ==========================================
# 函数定义
# ==========================================

function Find-Jdk21 {
    $jdksPattern = Join-Path (Join-Path $env:USERPROFILE ".jdks") "jdk-21*"
    $jdksDirs = Get-ChildItem -Path $jdksPattern -Directory -ErrorAction SilentlyContinue
    if ($jdksDirs -and $jdksDirs.Count -gt 0) {
        $jdkDir = $jdksDirs[0].FullName
        $contentsHome = Join-Path (Join-Path $jdkDir "Contents") "Home"
        if (Test-Path $contentsHome) { $jdkDir = $contentsHome }
        $javaExe = Join-Path (Join-Path $jdkDir "bin") "java.exe"
        if (-not (Test-Path $javaExe)) { $javaExe = Join-Path (Join-Path $jdkDir "bin") "java" }
        if (Test-Path $javaExe) {
            Write-Log "INFO" "JDK21 found in ~/.jdks"
            return $jdkDir
        } else {
            Write-Log "WARN" "~/.jdks incomplete, will try other options"
        }
    }

    $sysJava = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if (-not $sysJava) { $sysJava = Get-Command "java" -ErrorAction SilentlyContinue }
    if ($sysJava) {
        $javaExe = $sysJava.Source
        $versionOutput = & $javaExe -version 2>&1
        if ($versionOutput -match '"21"' -or $versionOutput -match '"21\.') {
            $javaHome = Split-Path -Parent (Split-Path -Parent $javaExe)
            Write-Log "INFO" "System JDK version check passed"
            return $javaHome
        }
        $jdkVersion = if ($versionOutput -match '([0-9]+\.[0-9]+\.[0-9]+)') { $Matches[1] } else { "unknown" }
        Write-Log "WARN" "JDK21 required, current version: $jdkVersion"
    }

    $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
    if (-not $javaHome) { $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine") }
    if ($javaHome) {
        $javaExe = Join-Path (Join-Path $javaHome "bin") "java.exe"
        if (-not (Test-Path $javaExe)) { $javaExe = Join-Path (Join-Path $javaHome "bin") "java" }
        if (Test-Path $javaExe) {
            $versionOutput = & $javaExe -version 2>&1
            if ($versionOutput -match '"21"' -or $versionOutput -match '"21\.') {
                Write-Log "INFO" "Found JAVA_HOME: $javaHome"
                return $javaHome
            }
        }
    }

    $localJava = Join-Path (Join-Path $JdksDir "bin") "java.exe"
    if (-not (Test-Path $localJava)) { $localJava = Join-Path (Join-Path $JdksDir "bin") "java" }
    if (Test-Path $localJava) {
        Write-Log "INFO" "Found local JDK: $JdksDir"
        return $JdksDir
    }

    $jdksPackageDir = Join-Path (Join-Path (Join-Path $BinDir "..") "..") "jdk21"
    if (-not (Test-Path $jdksPackageDir)) { $jdksPackageDir = Join-Path (Join-Path $LocalDir "..") "jdk21" }
    if (Test-Path $jdksPackageDir) {
        $zipFiles = Get-ChildItem -Path (Join-Path $jdksPackageDir "*.zip") -ErrorAction SilentlyContinue
        if ($zipFiles) {
            Write-Log "INFO" "Extracting JDK 21 from local package..."
            $zipFile = $zipFiles[0].FullName
            Expand-Archive -Path $zipFile -DestinationPath $JdksDir -Force
            $subDirs = Get-ChildItem -Path $JdksDir -Directory
            if ($subDirs -and $subDirs.Count -eq 1 -and $subDirs[0].Name -like "jdk-21*") {
                $extractedDir = $subDirs[0].FullName
                Get-ChildItem -Path $extractedDir | Move-Item -Destination $JdksDir -Force
                Remove-Item -Path $extractedDir -Recurse -Force
            }
            $javaExe = Join-Path (Join-Path $JdksDir "bin") "java.exe"
            if (-not (Test-Path $javaExe)) { $javaExe = Join-Path (Join-Path $JdksDir "bin") "java" }
            if (Test-Path $javaExe) {
                Write-Log "INFO" "JDK21 installed to $JdksDir"
                return $JdksDir
            }
        }
    }

    Write-Log "ERROR" "JDK 21 not found. Please run install.ps1 first."
    return $null
}

function Find-MatrixJar {
    $jarFiles = Get-ChildItem -Path (Join-Path $LocalDir "matrix-local-*.jar") -ErrorAction SilentlyContinue
    if (-not $jarFiles) { return $null }
    $jarFile = $jarFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1

    try {
        $stream = [System.IO.File]::OpenRead($jarFile.FullName)
        $reader = New-Object System.IO.BinaryReader($stream)
        $stream.Seek(-22, [System.IO.SeekOrigin]::End) | Out-Null
        $eocd = $reader.ReadBytes(4)
        $reader.Close()
        $stream.Close()
        if (-not ($eocd[0] -eq 0x50 -and $eocd[1] -eq 0x4B -and $eocd[2] -eq 0x05 -and $eocd[3] -eq 0x06)) {
            Write-Log "WARN" "JAR file may be incomplete: $($jarFile.Name)"
            return $null
        }
    } catch {
        Write-Log "WARN" "JAR validation failed: $($jarFile.Name)"
        return $null
    }
    Write-Log "INFO" "Found JAR: $($jarFile.Name) ($($jarFile.Length) bytes)"
    return $jarFile.FullName
}

function Stop-OldProcess {
    param([string]$PidFile, [string]$ProcessName)

    if (Test-Path $PidFile) {
        $oldPid = Get-Content $PidFile -Raw | ForEach-Object { $_.Trim() }
        if ($oldPid) {
            try {
                $proc = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
                if ($proc) {
                    Write-Log "INFO" "Stopping old process (PID: $oldPid)..."
                    $null = $proc.CloseMainWindow()
                    for ($i = 0; $i -lt 10; $i++) {
                        $procCheck = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
                        if (-not $procCheck) { break }
                        Start-Sleep -Seconds 1
                    }
                    $procCheck = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
                    if ($procCheck) {
                        Write-Log "WARN" "Old process did not exit, force killing"
                        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
                        Start-Sleep -Seconds 1
                    }
                } else {
                    Write-Log "INFO" "PID file exists but process not running, cleaning up"
                }
            } catch {
                Write-Log "WARN" "Error stopping process $oldPid : $_"
            }
        }
        Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
    }

    try {
        $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
        foreach ($proc in $procs) {
            if ($proc.CommandLine -match [regex]::Escape($ProcessName)) {
                Write-Log "WARN" "Found residual java process PID=$($proc.ProcessId), killing..."
                Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        Write-Log "WARN" "Cannot enumerate processes: $_"
    }
}

# 验证 Python 命令是否为真实有效的解释器
function Test-PythonCommand {
    param([string]$PythonPath)
    try {
        $result = & $PythonPath --version 2>&1
        if ($LASTEXITCODE -eq 0 -and $result -match "Python \d+") {
            return $true
        }
    } catch {
        # 忽略错误
    }
    return $false
}

function Start-WebuiProxyPython {
    param([string]$ProxyScript, [string]$WebuiPort = "10908", [string]$BackendPort = "10906")
    $webuiDir = Join-Path $LocalDir "webui"

    # 按优先级查找真正的 Python
    $pythonPath = $null
    $candidates = @(
        { Get-Command "python" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source },
        { Get-Command "python3" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source },
        { Join-Path $env:LOCALAPPDATA "Programs\Python\Python312\python.exe" },
        { Join-Path $env:LOCALAPPDATA "Programs\Python\Python311\python.exe" },
        { Join-Path $env:ProgramFiles "Python312\python.exe" },
        { Join-Path $env:ProgramFiles "Python311\python.exe" }
    )

    foreach ($candidate in $candidates) {
        $path = & $candidate
        if ($path -and (Test-Path $path)) {
            if (Test-PythonCommand -PythonPath $path) {
                $pythonPath = $path
                break
            }
        }
    }

    if (-not $pythonPath) {
        Write-Log "WARN" "No working Python installation found (not the Store stub)."
        return $false
    }

    if (-not (Test-Path $ProxyScript)) {
        Write-Log "WARN" "proxy_server.py not found: $ProxyScript"
        return $false
    }

    Write-Log "INFO" "Starting WebUI proxy with Python (port $WebuiPort -> backend $BackendPort)"
    Write-Log "INFO" "Python: $pythonPath"
    Write-Log "INFO" "Script: $ProxyScript"

    $logFile = Join-Path $LogsDir "webui.log"
    $arguments = "-u `"$ProxyScript`""
    $redirectCmd = "`"$pythonPath`" $arguments > `"$logFile`" 2>&1"

    $startupInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startupInfo.FileName = "cmd.exe"
    $startupInfo.Arguments = "/c $redirectCmd"
    $startupInfo.WorkingDirectory = $webuiDir
    $startupInfo.UseShellExecute = $false
    $startupInfo.CreateNoWindow = $true
    $startupInfo.EnvironmentVariables["MATRIX_WEBUI_DIR"] = $webuiDir
    $startupInfo.EnvironmentVariables["MATRIX_BACKEND_PORT"] = $BackendPort
    $startupInfo.EnvironmentVariables["MATRIX_WEBUI_PORT"] = $WebuiPort
    $startupInfo.EnvironmentVariables["MATRIX_WEBUI_HOST"] = "127.0.0.1"

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $startupInfo
    $proc.Start() | Out-Null

    Start-Sleep -Seconds 2

    if (-not $proc.HasExited) {
        Write-Log "INFO" "WebUI proxy started (127.0.0.1:$WebuiPort), PID=$($proc.Id)"
        $proc.Id | Out-File -FilePath $WebuiPidFile -Encoding UTF8 -Force
        return $true
    } else {
        Write-Log "WARN" "WebUI proxy failed (ExitCode: $($proc.ExitCode))"
        if (Test-Path $logFile) {
            $logContent = Get-Content $logFile -Raw -ErrorAction SilentlyContinue
            if ($logContent) { Write-Log "WARN" "Log: $logContent" }
        }
        return $false
    }
}

function Install-Python3 {
    if (-not $IsWindows) { return $false }
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Log "WARN" "Not running as Administrator, cannot auto-install Python"
        return $false
    }

    $installers = @(
        @{ Name = "winget"; Test = { Get-Command "winget" -ErrorAction SilentlyContinue }; Cmd = "winget install --id=Python.Python.3.12 --exact --silent --accept-package-agreements" },
        @{ Name = "choco";  Test = { Get-Command "choco" -ErrorAction SilentlyContinue };  Cmd = "choco install python3 -y" },
        @{ Name = "scoop";  Test = { Get-Command "scoop" -ErrorAction SilentlyContinue };  Cmd = "scoop install python" }
    )

    foreach ($installer in $installers) {
        $available = & $installer.Test
        if ($available) {
            Write-Log "INFO" "Installing Python 3 via $($installer.Name)..."
            $result = Invoke-Expression $installer.Cmd
            if ($LASTEXITCODE -eq 0) {
                Write-Log "INFO" "$($installer.Name) installation succeeded, refreshing PATH"
                $machinePath = [Environment]::GetEnvironmentVariable("PATH", "Machine")
                $userPath = [Environment]::GetEnvironmentVariable("PATH", "User")
                $env:PATH = "$machinePath;$userPath"
                return $true
            } else {
                Write-Log "WARN" "$($installer.Name) failed (exit code: $LASTEXITCODE)"
            }
        }
    }
    return $false
}

function Start-WebuiProxy {
    param([string]$ProxyScript)
    $webuiDir = Join-Path $LocalDir "webui"
    if (Start-WebuiProxyPython -ProxyScript $ProxyScript -WebuiPort 10908 -BackendPort 10906) {
        return $true
    }

    Write-Log "WARN" "WebUI proxy requires Python 3"
    if (Install-Python3) {
        Write-Log "INFO" "Python 3 installed, retrying WebUI proxy..."
        if (Start-WebuiProxyPython -ProxyScript $ProxyScript -WebuiPort 10908 -BackendPort 10906) {
            return $true
        }
    }

    Write-Log "ERROR" "WebUI 启动失败，Python 3 安装失败。请在管理员 PowerShell 执行以下指令，手动安装 Python 3："
    Write-Log "ERROR" "winget install --id=Python.Python.3.12 --exact --silent --accept-package-agreements"
    Write-Log "ERROR" ""
    return $false
}

# ==========================================
# 主逻辑
# ==========================================

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service Start (Windows)"
Write-Log "INFO" "=========================================="
Write-Log "INFO" "Install directory: $LocalDir"

# 1. 检测 JDK 21
$jdkHome = Find-Jdk21
if (-not $jdkHome) {
    Write-Log "ERROR" "JDK 21 not found. Please run install.ps1 first."
    exit 1
}
Write-Log "INFO" "Using JDK: $jdkHome"
$javaExe = Join-Path (Join-Path $jdkHome "bin") "java.exe"

# 2. 查找 JAR 文件
$jarFile = Find-MatrixJar
if (-not $jarFile) {
    Write-Log "ERROR" "matrix-local-*.jar not found. Please run install.ps1 first."
    exit 1
}

# 3. 检查日志目录
if (-not (Test-Path $LogsDir)) {
    New-Item -ItemType Directory -Path $LogsDir -Force | Out-Null
}

# 4. 停止旧进程
Write-Log "INFO" "Checking and stopping old processes..."
Stop-OldProcess -PidFile $ServicePidFile -ProcessName "matrix-local"
Stop-OldProcess -PidFile $WebuiPidFile -ProcessName "proxy_server"
Start-Sleep -Seconds 2

# 5. 检查配置文件
$configFile = Join-Path $ConfigDir "application.yml"
if (-not (Test-Path $configFile)) {
    Write-Log "WARN" "config/application.yml not found, using default configuration"
    $configFile = ""
}

# 6. 读取端口配置
$serverPort = "10906"
if (Test-Path $configFile) {
    $configContent = Get-Content $configFile -Raw -Encoding UTF8
    $portMatch = [regex]::Match($configContent, 'port:\s*(\d+)')
    if ($portMatch.Success) {
        $serverPort = $portMatch.Groups[1].Value
    }
}
Write-Log "INFO" "Server port: $serverPort"
Write-Log "INFO" "Log directory: $LogsDir"

# 7. 构建 Java 启动参数
$javaArgs = @(
    "-Xmx256m",
    "-Xms128m",
    "-Dfile.encoding=UTF-8",
    "-Dlogging.file.path=$LogsDir",
    "-Dserver.port=$serverPort",
    "-jar",
    $jarFile
)

$serviceLogFile = Join-Path $LogsDir "app.log"
Write-Log "INFO" "Starting Matrix backend..."
Write-Log "INFO" "Java command: $javaExe $javaArgs"

# 8. 启动后端服务（.NET Process + 异步日志）
try {
    $procInfo = New-Object System.Diagnostics.ProcessStartInfo
    $procInfo.FileName = $javaExe
    $procInfo.Arguments = $javaArgs -join ' '
    $procInfo.WorkingDirectory = $LocalDir
    $procInfo.UseShellExecute = $false
    $procInfo.RedirectStandardOutput = $true
    $procInfo.RedirectStandardError = $true
    $procInfo.CreateNoWindow = $true
    $procInfo.EnvironmentVariables["MATRIX_HOME"] = $LocalDir

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $procInfo

    $outEvent = Register-ObjectEvent -InputObject $proc -EventName 'OutputDataReceived' -Action {
        $data = $EventArgs.Data
        if ($data) {
            Add-Content -Path $Event.MessageData -Value $data -Encoding UTF8
        }
    } -MessageData $serviceLogFile
    $errEvent = Register-ObjectEvent -InputObject $proc -EventName 'ErrorDataReceived' -Action {
        $data = $EventArgs.Data
        if ($data) {
            Add-Content -Path $Event.MessageData -Value $data -Encoding UTF8
        }
    } -MessageData $serviceLogFile

    $proc.Start() | Out-Null
    $proc.BeginOutputReadLine()
    $proc.BeginErrorReadLine()

    $proc.Id | Out-File -FilePath $ServicePidFile -Encoding UTF8 -Force
    Write-Log "INFO" "Backend started, PID=$($proc.Id)"
    Write-Log "INFO" "Log file: $serviceLogFile"

    Start-Sleep -Seconds 3

    if ($proc.HasExited) {
        Write-Log "ERROR" "Backend process exited immediately (ExitCode: $($proc.ExitCode))"
        Start-Sleep -Seconds 1
        if (Test-Path $serviceLogFile) {
            Write-Log "ERROR" "Log content:"
            Get-Content $serviceLogFile -ErrorAction SilentlyContinue | ForEach-Object { Write-Log "ERROR" $_ }
        }
        exit 1
    }
} catch {
    Write-Log "ERROR" "Failed to start backend: $_"
    exit 1
}

# 9. 启动 WebUI 代理
$proxyScript = Join-Path $BinDir "proxy_server.py"
if (-not (Test-Path $proxyScript)) {
    $fallbackScript = Join-Path $ScriptDir "proxy_server.py"
    if (Test-Path $fallbackScript) {
        Write-Log "INFO" "Using proxy_server.py from script directory"
        $proxyScript = $fallbackScript
    } else {
        Write-Log "WARN" "proxy_server.py not found, WebUI proxy will not start"
        $proxyScript = $null
    }
}

$webuiDir = Join-Path $LocalDir "webui"
if ($proxyScript -and (Test-Path $webuiDir)) {
    if (Test-Path (Join-Path $webuiDir "index.html")) {
        Write-Log "INFO" "WebUI directory: $webuiDir"
        if (-not (Start-WebuiProxy -ProxyScript $proxyScript)) {
            Write-Log "WARN" "WebUI proxy failed to start, but backend is still running"
        }
    } else {
        Write-Log "INFO" "WebUI directory missing index.html, skipping WebUI"
        Write-Log "INFO" "Run 'matrix update' to get WebUI"
    }
} elseif (-not $proxyScript) {
    Write-Log "WARN" "Proxy script missing, skipping WebUI"
} else {
    Write-Log "INFO" "WebUI directory does not exist, skipping WebUI"
    Write-Log "INFO" "Run 'matrix update' to get WebUI"
}

# 10. 完成提示
Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service Started"
Write-Log "INFO" "=========================================="
Write-Log "INFO" "Backend: http://localhost:$serverPort"
Write-Log "INFO" "WebUI:   http://localhost:10908 (API proxy to :10906/v1)"
Write-Log "INFO" "Logs:    $LogsDir"
Write-Log "INFO" "Stop:    $BinDir\stop.ps1"