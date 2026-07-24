<#
.SYNOPSIS
    Matrix 本地服务启动脚本 (Windows)
.DESCRIPTION
    检测 JDK 21，启动 Matrix 后端 Java 服务，启动 WebUI 代理服务
.NOTES
    对应 start.sh 的 PowerShell 实现
    版本: 1.0.2
#>

# ==========================================
# 初始化
# ==========================================

# 编码设置
$OutputEncoding = [Text.Encoding]::UTF8
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# 路径定义
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$BinDir = Join-Path $LocalDir "bin"          # 固定 bin 目录，不依赖脚本位置
$JdksDir = Join-Path $MatrixHome "jdk21"
$LogsDir = Join-Path $LocalDir "logs"
$ConfigDir = Join-Path $LocalDir "config"

# ---- 日志函数 ----
function Write-Log {
    param(
        [string]$Level = "INFO",
        [string]$Message
    )
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

# ---- PID 文件管理 ----
$ServicePidFile = Join-Path $BinDir "app.pid"
$WebuiPidFile = Join-Path $BinDir "webui.pid"

# 确保 bin 目录存在（用于 PID 文件）
if (-not (Test-Path $BinDir)) {
    New-Item -ItemType Directory -Path $BinDir -Force | Out-Null
}

# ==========================================
# 函数定义
# ==========================================

# ---- 检测 JDK 21 ----
function Find-Jdk21 {
    # ---- 优先级1: 检查 ~/.jdks/jdk-21* (IDE JDK) ----
    $jdksPattern = Join-Path (Join-Path $env:USERPROFILE ".jdks") "jdk-21*"
    $jdksDirs = Get-ChildItem -Path $jdksPattern -Directory -ErrorAction SilentlyContinue
    if ($jdksDirs -and $jdksDirs.Count -gt 0) {
        $jdkDir = $jdksDirs[0].FullName
        # 处理 macOS .jdk/Contents/Home 结构
        $contentsHome = Join-Path (Join-Path $jdkDir "Contents") "Home"
        if (Test-Path $contentsHome) {
            $jdkDir = $contentsHome
        }
        $javaExe = Join-Path (Join-Path $jdkDir "bin") "java.exe"
        if (-not (Test-Path $javaExe)) {
            $javaExe = Join-Path (Join-Path $jdkDir "bin") "java"
        }
        if (Test-Path $javaExe) {
            Write-Log "INFO" "✓ JDK21已安装（跳过安装步骤）"
            return $jdkDir
        } else {
            Write-Log "WARN" "~/.jdks目录不完整，将重新安装"
        }
    }

    # ---- 优先级2: 检查系统 PATH 中的 java (java -version) ----
    $sysJava = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if (-not $sysJava) {
        $sysJava = Get-Command "java" -ErrorAction SilentlyContinue
    }
    if ($sysJava) {
        $javaExe = $sysJava.Source
        $versionOutput = & $javaExe -version 2>&1
        if ($versionOutput -match '"21"' -or $versionOutput -match '"21\.') {
            $javaHome = Split-Path -Parent (Split-Path -Parent $javaExe)
            Write-Log "INFO" "✓系统JDK版本检查通过"
            return $javaHome
        }
        $jdkVersion = if ($versionOutput -match '([0-9]+\.[0-9]+\.[0-9]+)') { $Matches[1] } else { "未知" }
        Write-Log "WARN" "需要JDK21，当前版本为 $jdkVersion"
    }

    # ---- 优先级3: 检查 JAVA_HOME 环境变量 ----
    $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
    if (-not $javaHome) {
        $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
    }
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

    # ---- 优先级4: 检查 ~/.matrix/jdk21 (本地捆绑 JDK) ----
    $localJava = Join-Path (Join-Path $JdksDir "bin") "java.exe"
    if (-not (Test-Path $localJava)) {
        $localJava = Join-Path (Join-Path $JdksDir "bin") "java"
    }
    if (Test-Path $localJava) {
        Write-Log "INFO" "找到本地 JDK: $JdksDir"
        return $JdksDir
    }

    # ---- 优先级5: 从本地 jdk21/ 目录解压 ----
    $jdksPackageDir = Join-Path (Join-Path (Join-Path $BinDir "..") "..") "jdk21"
    if (-not (Test-Path $jdksPackageDir)) {
        $jdksPackageDir = Join-Path (Join-Path $LocalDir "..") "jdk21"
    }
    if (Test-Path $jdksPackageDir) {
        $bundledZip = Join-Path $jdksPackageDir "*.zip"
        $zipFiles = Get-ChildItem -Path $bundledZip -ErrorAction SilentlyContinue
        if ($zipFiles -and $zipFiles.Count -gt 0) {
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
                Write-Log "INFO" "✓ JDK21已安装到 $JdksDir"
                return $JdksDir
            }
        }
    }

    Write-Log "ERROR" "未找到 JDK 21，请先运行 install.ps1 完成安装"
    return $null
}

# ---- 查找 JAR 文件 ----
function Find-MatrixJar {
    $jarPattern = Join-Path $LocalDir "matrix-local-*.jar"
    $jarFiles = Get-ChildItem -Path $jarPattern -ErrorAction SilentlyContinue
    if (-not $jarFiles -or $jarFiles.Count -eq 0) {
        return $null
    }

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

# ---- 停止旧进程 ----
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

# ---- 启动 WebUI 代理（Python） ----
function Start-WebuiProxyPython {
    param(
        [string]$ProxyScript,
        [string]$WebuiPort = "10908",
        [string]$BackendPort = "10906"
    )

    $webuiDir = Join-Path $LocalDir "webui"

    $pythonCmd = Get-Command "python3" -ErrorAction SilentlyContinue
    if (-not $pythonCmd) {
        $pythonCmd = Get-Command "python" -ErrorAction SilentlyContinue
    }

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
    $startupInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startupInfo.FileName = $pythonCmd.Source
    $startupInfo.Arguments = "-u `"$ProxyScript`""
    $startupInfo.WorkingDirectory = $webuiDir
    $startupInfo.RedirectStandardOutput = $true
    $startupInfo.RedirectStandardError = $true
    $startupInfo.UseShellExecute = $false
    $startupInfo.CreateNoWindow = $true

    # 环境变量：强制监听 127.0.0.1，避免防火墙拦截
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
        return $true
    } else {
        $stdout = $proc.StandardOutput.ReadToEnd()
        $stderr = $proc.StandardError.ReadToEnd()
        Write-Log "WARN" "WebUI 代理启动失败 (ExitCode: $($proc.ExitCode))"
        if ($stdout) { Write-Log "WARN" "STDOUT: $stdout" }
        if ($stderr) { Write-Log "WARN" "STDERR: $stderr" }
        return $false
    }
}

# ---- 自动安装 Python 3 (Windows) ----
function Install-Python3 {
    # 仅在 Windows 上有包管理器时执行
    if (-not $IsWindows) {
        return $false
    }

    # 检测管理员权限
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Log "WARN" "当前非管理员权限，无法自动安装 Python 3"
        return $false
    }

    # 按优先级检测包管理器并安装
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
                # 从注册表刷新 PATH，使新安装的 Python 在当前进程中立即可用
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

# ---- 启动 WebUI 代理（主入口） ----
function Start-WebuiProxy {
    param([string]$ProxyScript)

    $webuiDir = Join-Path $LocalDir "webui"
    $backendUrl = "http://localhost:10906"

    # 优先尝试 Python 代理
    if (Start-WebuiProxyPython -ProxyScript $ProxyScript -WebuiPort 10908 -BackendPort 10906) {
        return $true
    }

    # Python 不可用，尝试自动安装
    Write-Log "WARN" "WebUI 代理需要 Python 3 才能启动"

    if (Install-Python3) {
        Write-Log "INFO" "Python 3 自动安装完成，正在启动 WebUI 代理..."
        if (Start-WebuiProxyPython -ProxyScript $ProxyScript -WebuiPort 10908 -BackendPort 10906) {
            return $true
        }
    }

    # 所有方式均失败，输出详细手动安装指引
    Write-Log "ERROR" "未能启动 WebUI 代理，Python 3 不可用且自动安装失败"
    Write-Log "INFO"  "请手动安装 Python 3："
    Write-Log "INFO"  "  方案 1：从 https://www.python.org/downloads/ 下载安装包"
    Write-Log "INFO"  "         安装时务必勾选 'Add Python to PATH'"
    Write-Log "INFO"  "  方案 2：通过 winget 安装（管理员 PowerShell）："
    Write-Log "INFO"  "         winget install --id=Python.Python.3.12 --exact"
    Write-Log "INFO"  "  方案 3：通过 Chocolatey 安装（管理员 PowerShell）："
    Write-Log "INFO"  "         choco install python3 -y"
    Write-Log "INFO"  "  安装后重新运行 start.ps1 即可"
    Write-Log "INFO"  "  或手动运行 Python 代理：python3 $(Resolve-Path $ProxyScript)"
    return $false
}

# ==========================================
# 主逻辑
# ==========================================

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service Start (Windows)"
Write-Log "INFO" "=========================================="
Write-Log "INFO" "安装目录: $LocalDir"

# 1. 检测 JDK 21
$jdkHome = Find-Jdk21
if (-not $jdkHome) {
    Write-Log "ERROR" "未找到 JDK 21"
    Write-Log "INFO" "请先运行 install.ps1 完成安装"
    exit 1
}
Write-Log "INFO" "使用 JDK: $jdkHome"
$javaExe = Join-Path (Join-Path $jdkHome "bin") "java.exe"

# 2. 找到 JAR 文件
$jarFile = Find-MatrixJar
if (-not $jarFile) {
    Write-Log "ERROR" "未找到 matrix-local-*.jar 文件"
    Write-Log "INFO" "请先运行 install.ps1 完成安装"
    exit 1
}

# 3. 检查日志目录
if (-not (Test-Path $LogsDir)) {
    New-Item -ItemType Directory -Path $LogsDir -Force | Out-Null
}

# 4. 停止旧进程
Write-Log "INFO" "检查并停止旧进程..."
Stop-OldProcess -PidFile $ServicePidFile -ProcessName "matrix-local"
Stop-OldProcess -PidFile $WebuiPidFile -ProcessName "proxy_server"
Start-Sleep -Seconds 2

# 5. 检查 config 文件
$configFile = Join-Path $ConfigDir "application.yml"
if (-not (Test-Path $configFile)) {
    Write-Log "WARN" "未找到 config/application.yml，将使用默认配置"
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
Write-Log "INFO" "服务端口: $serverPort"
Write-Log "INFO" "日志目录: $LogsDir"

# 7. JVM 参数
$jvmArgs = @(
    "-Xmx256m",
    "-Xms128m",
    "-Dfile.encoding=UTF-8",
    "-Dlogging.file.path=`"$LogsDir`"",
    "-Dserver.port=$serverPort"
)

# 8. 启动后端服务
$serviceLogFile = Join-Path $LogsDir "app.log"
Write-Log "INFO" "正在启动 Matrix 后端服务..."

try {
    $startupInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startupInfo.FileName = $javaExe
    $startupInfo.Arguments = "$($jvmArgs -join ' ') -jar `"$jarFile`""
    $startupInfo.WorkingDirectory = $LocalDir
    $startupInfo.RedirectStandardOutput = $true
    $startupInfo.RedirectStandardError = $true
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
        $stderrOutput = $proc.StandardError.ReadToEnd()
        if ($stderrOutput) {
            Write-Log "ERROR" "错误输出: $stderrOutput"
        }
        exit 1
    }
} catch {
    Write-Log "ERROR" "启动后端服务失败: $_"
    exit 1
}

# 9. 启动 WebUI
# 优先从固定 bin 目录查找 proxy_server.py，若不存在则回退到脚本所在目录
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

# 10. 完成提示
Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix Local Service 启动完成"
Write-Log "INFO" "=========================================="
Write-Log "INFO" "后端服务: http://localhost:$serverPort"
Write-Log "INFO" "WebUI:    http://localhost:10908 (API 自动代理至 :10906/v1)"
Write-Log "INFO" "日志目录: $LogsDir"
Write-Log "INFO" "停止服务: $BinDir\stop.ps1"
