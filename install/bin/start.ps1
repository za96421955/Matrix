<#
.SYNOPSIS
    Matrix 本地服务启动脚本 (Windows)
.DESCRIPTION
    检测 JDK 21，启动 Matrix 后端 Java 服务，启动 WebUI 代理服务
.NOTES
    对应 start.sh 的 PowerShell 实现
    版本: 1.0.3
#>

# ==========================================
# 初始化
# ==========================================

$OutputEncoding = [Text.Encoding]::UTF8
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$BinDir = Join-Path $LocalDir "bin"          # 固定 bin 目录，不依赖脚本位置
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
        if (Test-Path $contentsHome) {
            $jdkDir = $contentsHome
        }
        $javaExe = Join-Path (Join-Path $jdkDir "bin") "java.exe"
        if (-not (Test-Path $javaExe)) {
            $javaExe = Join-Path (Join-Path $jdkDir "bin") "java"
        }
        if (Test-Path $javaExe) {
            Write-Log "INFO" "JDK21已安装（跳过安装步骤）"
            return $jdkDir
        } else {
            Write-Log "WARN" "~/.jdks目录不完整，将重新安装"
        }
    }

    $sysJava = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if (-not $sysJava) { $sysJava = Get-Command "java" -ErrorAction SilentlyContinue }
    if ($sysJava) {
        $javaExe = $sysJava.Source
        $versionOutput = & $javaExe -version 2>&1
        if ($versionOutput -match '"21"' -or $versionOutput -match '"21\.') {
            $javaHome = Split-Path -Parent (Split-Path -Parent $javaExe)
            Write-Log "INFO" "系统JDK版本检查通过"
            return $javaHome
        }
        $jdkVersion = if ($versionOutput -match '([0-9]+\.[0-9]+\.[0-9]+)') { $Matches[1] } else { "未知" }
        Write-Log "WARN" "需要JDK21，当前版本为 $jdkVersion"
    }

    $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
    if (-not $javaHome) { $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine") }
    if ($javaHome) {
        $javaExe = Join-Path (Join-Path $javaHome "bin") "java.exe"
        if (-not (Test-Path $javaExe)) {
            $javaExe = Join-Path (Join-Path $javaHome "bin") "java"
        }
        if (Test-Path $javaExe) {
            $versionOutput = & $javaExe -version 2>&1
            if ($versionOutput -match '"21"' -or $versionOutput -match '"21\.') {
                Write-Log "INFO" "找到 JAVA_HOME: $javaHome"
                return $javaHome
            }
        }
    }

    $localJava = Join-Path (Join-Path $JdksDir "bin") "java.exe"
    if (-not (Test-Path $localJava)) {
        $localJava = Join-Path (Join-Path $JdksDir "bin") "java"
    }
    if (Test-Path $localJava) {
        Write-Log "INFO" "找到本地 JDK: $JdksDir"
        return $JdksDir
    }

    $jdksPackageDir = Join-Path (Join-Path (Join-Path $BinDir "..") "..") "jdk21"
    if (-not (Test-Path $jdksPackageDir)) {
        $jdksPackageDir = Join-Path (Join-Path $LocalDir "..") "jdk21"
    }
    if (Test-Path $jdksPackageDir) {
        $zipFiles = Get-ChildItem -Path (Join-Path $jdksPackageDir "*.zip") -ErrorAction SilentlyContinue
        if ($zipFiles) {
            Write-Log "INFO" "从本地安装目录解压 JDK 21..."
            $zipFile = $zipFiles[0].FullName
            Expand-Archive -Path $zipFile -DestinationPath $JdksDir -Force
            $subDirs = Get-ChildItem -Path $JdksDir -Directory
            if ($subDirs -and $subDirs.Count -eq 1 -and $subDirs[0].Name -like "jdk-21*") {
                $extractedDir = $subDirs[0].FullName
                Get-ChildItem -Path $extractedDir | Move-Item -Destination $JdksDir -Force
                Remove-Item -Path $extractedDir -Recurse -Force
            }
            $javaExe = Join-Path (Join-Path $JdksDir "bin") "java.exe"
            if (-not (Test-Path $javaExe)) {
                $javaExe = Join-Path (Join-Path $JdksDir "bin") "java"
            }
            if (Test-Path $javaExe) {
                Write-Log "INFO" "JDK21已安装到 $JdksDir"
                return $JdksDir
            }
        }
    }

    Write-Log "ERROR" "未找到 JDK 21，请先运行 install.ps1 完成安装"
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
            Write-Log "WARN" "JAR 文件可能不完整 (EOCD 签名不匹配): $($jarFile.Name)"
            return $null
        }
    } catch {
        Write-Log "WARN" "JAR 文件校验失败: $($jarFile.Name)"
        return $null
    }

    Write-Log "INFO" "找到 JAR 文件: $($jarFile.Name) ($($jarFile.Length) bytes)"
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
                    Write-Log "INFO" "检测到旧进程 (PID: $oldPid)，正在停止..."
                    $null = $proc.CloseMainWindow()
                    for ($i = 0; $i -lt 10; $i++) {
                        $procCheck = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
                        if (-not $procCheck) { break }
                        Start-Sleep -Seconds 1
                    }
                    $procCheck = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
                    if ($procCheck) {
                        Write-Log "WARN" "旧进程未在10秒内退出，强制终止"
                        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
                        Start-Sleep -Seconds 1
                    }
                } else {
                    Write-Log "INFO" "旧PID文件存在但进程已不存在，清理文件"
                }
            } catch {
                Write-Log "WARN" "停止进程 $oldPid 时出错: $_"
            }
        }
        Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
    }

    try {
        $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
        foreach ($proc in $procs) {
            $cmdLine = $proc.CommandLine
            if ($cmdLine -and $cmdLine -match [regex]::Escape($ProcessName)) {
                Write-Log "WARN" "发现残留 java 进程 PID=$($proc.ProcessId)，强制清理..."
                Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        try {
            $procs = Get-WmiObject Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
            foreach ($proc in $procs) {
                $cmdLine = $proc.CommandLine
                if ($cmdLine -and $cmdLine -match [regex]::Escape($ProcessName)) {
                    Write-Log "WARN" "发现残留 java 进程 PID=$($proc.ProcessId)，强制清理..."
                    Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
                }
            }
        } catch {
            Write-Log "WARN" "无法枚举系统进程: $_"
        }
    }
}

function Start-WebuiProxyPython {
    param(
        [string]$ProxyScript,
        [string]$WebuiPort = "10908",
        [string]$BackendPort = "10906"
    )

    $webuiDir = Join-Path $LocalDir "webui"

    $pythonCmd = Get-Command "python3" -ErrorAction SilentlyContinue
    if (-not $pythonCmd) { $pythonCmd = Get-Command "python" -ErrorAction SilentlyContinue }

    if (-not $pythonCmd) {
        Write-Log "WARN" "未找到 Python 解释器"
        return $false
    }

    if (-not (Test-Path $ProxyScript)) {
        Write-Log "WARN" "proxy_server.py 未找到: $ProxyScript"
        return $false
    }

    Write-Log "INFO" "使用 Python 启动 WebUI 代理 (端口 $WebuiPort -> 后端 $BackendPort)"
    Write-Log "INFO" "Python 路径: $($pythonCmd.Source)"
    Write-Log "INFO" "代理脚本: $ProxyScript"

    $logFile = Join-Path $LogsDir "webui.log"

    # 构建命令行：使用 cmd.exe /c 进行重定向，避免管道问题
    $pythonExe = $pythonCmd.Source
    # 将路径加双引号保护，使用两对双引号转义（"" 在双引号字符串中表示一个双引号）
    $arguments = "-u ""$ProxyScript"""
    $redirectCmd = """$pythonExe"" $arguments > ""$logFile"" 2>&1"

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
        Write-Log "INFO" "WebUI 代理已启动 (监听 127.0.0.1:$WebuiPort)，PID=$($proc.Id)"
        $proc.Id | Out-File -FilePath $WebuiPidFile -Encoding UTF8 -Force
        return $true
    } else {
        Write-Log "WARN" "WebUI 代理启动失败 (ExitCode: $($proc.ExitCode))"
        if (Test-Path $logFile) {
            $logContent = Get-Content $logFile -Raw -ErrorAction SilentlyContinue
            if ($logContent) {
                Write-Log "WARN" "日志输出: $logContent"
            }
        }
        return $false
    }
}

function Install-Python3 {
    if (-not $IsWindows) { return $false }

    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Log "WARN" "当前非管理员权限，无法自动安装 Python 3"
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
            Write-Log "INFO" "通过 $($installer.Name) 安装 Python 3..."
            $result = Invoke-Expression $installer.Cmd
            if ($LASTEXITCODE -eq 0) {
                Write-Log "INFO" "$($installer.Name) 安装成功，正在刷新 PATH 环境变量..."
                $machinePath = [Environment]::GetEnvironmentVariable("PATH", "Machine")
                $userPath = [Environment]::GetEnvironmentVariable("PATH", "User")
                $env:PATH = "$machinePath;$userPath"
                return $true
            } else {
                Write-Log "WARN" "$($installer.Name) 安装失败（退出码: $LASTEXITCODE），尝试下一种方式..."
            }
        }
    }

    return $false
}

function Start-WebuiProxy {
    param([string]$ProxyScript)

    $webuiDir = Join-Path $LocalDir "webui"
    $backendUrl = "http://localhost:10906"

    if (Start-WebuiProxyPython -ProxyScript $ProxyScript -WebuiPort 10908 -BackendPort 10906) {
        return $true
    }

    Write-Log "WARN" "WebUI 代理需要 Python 3 才能启动"

    if (Install-Python3) {
        Write-Log "INFO" "Python 3 自动安装完成，正在启动 WebUI 代理..."
        if (Start-WebuiProxyPython -ProxyScript $ProxyScript -WebuiPort 10908 -BackendPort 10906) {
            return $true
        }
    }

    Write-Log "ERROR" "未能启动 WebUI 代理，Python 3 不可用且自动安装失败"
    Write-Log "INFO" "请手动安装 Python 3："
    Write-Log "INFO" "  方案 1：从 https://www.python.org/downloads/ 下载安装包"
    Write-Log "INFO" "         安装时务必勾选 'Add Python to PATH'"
    Write-Log "INFO" "  方案 2：通过 winget 安装（管理员 PowerShell）："
    Write-Log "INFO" "         winget install --id=Python.Python.3.12 --exact"
    Write-Log "INFO" "  方案 3：通过 Chocolatey 安装（管理员 PowerShell）："
    Write-Log "INFO" "         choco install python3 -y"
    Write-Log "INFO" "  安装后重新运行 start.ps1 即可"
    Write-Log "INFO" "  或手动运行 Python 代理：python3 $(Resolve-Path $ProxyScript)"
    return $false
}

# ==========================================
# 主逻辑
# ==========================================

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service Start (Windows)"
Write-Log "INFO" "=========================================="
Write-Log "INFO" "安装目录: $LocalDir"

$jdkHome = Find-Jdk21
if (-not $jdkHome) {
    Write-Log "ERROR" "未找到 JDK 21"
    Write-Log "INFO" "请先运行 install.ps1 完成安装"
    exit 1
}
Write-Log "INFO" "使用 JDK: $jdkHome"
$javaExe = Join-Path (Join-Path $jdkHome "bin") "java.exe"

$jarFile = Find-MatrixJar
if (-not $jarFile) {
    Write-Log "ERROR" "未找到 matrix-local-*.jar 文件"
    Write-Log "INFO" "请先运行 install.ps1 完成安装"
    exit 1
}

if (-not (Test-Path $LogsDir)) {
    New-Item -ItemType Directory -Path $LogsDir -Force | Out-Null
}

Write-Log "INFO" "检查并停止旧进程..."
Stop-OldProcess -PidFile $ServicePidFile -ProcessName "matrix-local"
Stop-OldProcess -PidFile $WebuiPidFile -ProcessName "proxy_server"
Start-Sleep -Seconds 2

$configFile = Join-Path $ConfigDir "application.yml"
if (-not (Test-Path $configFile)) {
    Write-Log "WARN" "未找到 config/application.yml，将使用默认配置"
    $configFile = ""
}

$serverPort = "10906"
if (Test-Path $configFile) {
    $configContent = Get-Content $configFile -Raw -Encoding UTF8
    $portMatch = [regex]::Match($configContent, 'port:\s*(\d+)')
    if ($portMatch.Success) {
        $serverPort = $portMatch.Groups[1].Value
    }
}
Write-Log "INFO" "服务端口: $serverPort"
Write-Log "INFO" "日志目录: $LogsDir"

$jvmArgs = @(
    "-Xmx256m",
    "-Xms128m",
    "-Dfile.encoding=UTF-8",
    "-Dlogging.file.path=""$LogsDir""",
    "-Dserver.port=$serverPort"
)

$serviceLogFile = Join-Path $LogsDir "app.log"
Write-Log "INFO" "正在启动 Matrix 后端服务..."

try {
    $javaCmd = """$javaExe"" $($jvmArgs -join ' ') -jar ""$jarFile"""
    $redirectCmd = "$javaCmd > ""$serviceLogFile"" 2>&1"

    $startupInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startupInfo.FileName = "cmd.exe"
    $startupInfo.Arguments = "/c $redirectCmd"
    $startupInfo.WorkingDirectory = $LocalDir
    $startupInfo.UseShellExecute = $false
    $startupInfo.CreateNoWindow = $true
    $startupInfo.EnvironmentVariables["MATRIX_HOME"] = $LocalDir
    $startupInfo.EnvironmentVariables["LOGGING_FILE_PATH"] = $LogsDir

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $startupInfo
    $proc.Start() | Out-Null

    $proc.Id | Out-File -FilePath $ServicePidFile -Encoding UTF8 -Force
    Write-Log "INFO" "后端服务已启动，PID=$($proc.Id)"
    Write-Log "INFO" "日志文件: $serviceLogFile"

    Start-Sleep -Seconds 3
    $checkProc = Get-Process -Id $proc.Id -ErrorAction SilentlyContinue
    if (-not $checkProc) {
        Write-Log "ERROR" "后端服务启动失败，进程已退出"
        if (Test-Path $serviceLogFile) {
            $logContent = Get-Content $serviceLogFile -Raw -ErrorAction SilentlyContinue
            if ($logContent) {
                Write-Log "ERROR" "日志输出: $logContent"
            }
        }
        exit 1
    }
} catch {
    Write-Log "ERROR" "启动后端服务失败: $_"
    exit 1
}

# 查找 proxy_server.py
$proxyScript = Join-Path $BinDir "proxy_server.py"
if (-not (Test-Path $proxyScript)) {
    $fallbackScript = Join-Path $ScriptDir "proxy_server.py"
    if (Test-Path $fallbackScript) {
        Write-Log "INFO" "在 bin 目录未找到 proxy_server.py，使用脚本同目录下的文件"
        $proxyScript = $fallbackScript
    } else {
        Write-Log "WARN" "proxy_server.py 未找到，WebUI 代理无法启动"
        $proxyScript = $null
    }
}

$webuiDir = Join-Path $LocalDir "webui"
if ($proxyScript -and (Test-Path $webuiDir)) {
    if (Test-Path (Join-Path $webuiDir "index.html")) {
        Write-Log "INFO" "WebUI 目录: $webuiDir"
        if (-not (Start-WebuiProxy -ProxyScript $proxyScript)) {
            Write-Log "WARN" "WebUI 代理启动失败，但后端服务已正常运行"
        }
    } else {
        Write-Log "INFO" "WebUI 目录存在但缺少 index.html，跳过 WebUI 启动"
        Write-Log "INFO" "如需 WebUI，请执行: matrix update"
    }
} elseif (-not $proxyScript) {
    Write-Log "WARN" "未找到代理脚本，跳过 WebUI 启动"
} else {
    Write-Log "INFO" "WebUI 目录不存在，跳过 WebUI 启动"
    Write-Log "INFO" "如需 WebUI，请执行: matrix update"
}

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service 启动完成"
Write-Log "INFO" "=========================================="
Write-Log "INFO" "后端服务: http://localhost:$serverPort"
Write-Log "INFO" "WebUI:    http://localhost:10908 (API 自动代理至 :10906/v1)"
Write-Log "INFO" "日志目录: $LogsDir"
Write-Log "INFO" "停止服务: $BinDir\stop.ps1"