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
$BinDir = $ScriptDir

$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
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
$PidDir = Join-Path $LocalDir "tmp"
$ServicePidFile = Join-Path $PidDir "matrix-local.pid"
$WebuiPidFile = Join-Path $PidDir "matrix-webui.pid"

# 创建 tmp 目录
if (-not (Test-Path $PidDir)) {
    New-Item -ItemType Directory -Path $PidDir -Force | Out-Null
}

# ==========================================
# 函数定义
# ==========================================

# ---- 检测 JDK 21 ----
function Find-Jdk21 {
    # 优先级1: 检查 ~/.matrix/jdk21/bin/java.exe
    $localJava = Join-Path $JdksDir "bin" "java.exe"
    if (Test-Path $localJava) {
        Write-Log "INFO" "找到本地 JDK: $JdksDir"
        return $JdksDir
    }

    # 优先级2: 检查 ~/.jdks/jdk-21*
    $jdksPattern = Join-Path $env:USERPROFILE ".jdks" "jdk-21*"
    $jdksDirs = Get-ChildItem -Path $jdksPattern -Directory -ErrorAction SilentlyContinue
    if ($jdksDirs -and $jdksDirs.Count -gt 0) {
        $jdkDir = $jdksDirs[0].FullName
        $javaExe = Join-Path $jdkDir "bin" "java.exe"
        if (Test-Path $javaExe) {
            Write-Log "INFO" "找到 IDE JDK: $jdkDir"
            return $jdkDir
        }
    }

    # 优先级3: 检查 JAVA_HOME 环境变量
    $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
    if (-not $javaHome) {
        $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
    }
    if ($javaHome) {
        $javaExe = Join-Path $javaHome "bin" "java.exe"
        if (Test-Path $javaExe) {
            $versionOutput = & $javaExe -version 2>&1
            if ($versionOutput -match '"(\d+)') {
                $versionMajor = $Matches[1]
                if ($versionMajor -eq "21") {
                    Write-Log "INFO" "找到 JAVA_HOME: $javaHome"
                    return $javaHome
                }
            }
        }
    }

    # 优先级4: 检查系统 PATH 中的 java
    $sysJava = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($sysJava) {
        $javaExe = $sysJava.Source
        $versionOutput = & $javaExe -version 2>&1
        if ($versionOutput -match '"(\d+)') {
            $versionMajor = $Matches[1]
            if ($versionMajor -eq "21") {
                $javaHome = Split-Path -Parent (Split-Path -Parent $javaExe)
                Write-Log "INFO" "找到系统 PATH 中的 JDK 21: $javaHome"
                return $javaHome
            }
        }
    }

    # 如果都没找到，尝试从本地 install 目录的 jdk21 解压
    $bundledJdk = Join-Path $LocalDir ".." "jdk21"
    $bundledJdk = Resolve-Path $bundledJdk -ErrorAction SilentlyContinue
    if ($bundledJdk) {
        $bundledZip = Join-Path $bundledJdk "*.zip"
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
            $javaExe = Join-Path $JdksDir "bin" "java.exe"
            if (Test-Path $javaExe) {
                Write-Log "INFO" "JDK 21 安装完成: $JdksDir"
                return $JdksDir
            }
        }
    }

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

    # 校验 JAR 完整性
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
                    Write-Log "INFO" "检测到旧进程 PID=$oldPid，正在停止..."
                    Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
                    Start-Sleep -Seconds 2
                    $procCheck = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
                    if ($procCheck) {
                        Write-Log "WARN" "进程 $oldPid 未能正常停止，强制终止"
                        Stop-Process -Id $oldPid -Force
                    }
                }
            } catch {
                Write-Log "WARN" "停止进程 $oldPid 时出错: $_"
            }
        }
    }

    # 通过命令行匹配
    try {
        $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
        foreach ($proc in $procs) {
            $cmdLine = $proc.CommandLine
            if ($cmdLine -and $cmdLine -match [regex]::Escape($ProcessName)) {
                Write-Log "INFO" "通过命令行匹配到旧进程 PID=$($proc.ProcessId)，正在停止..."
                Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        try {
            $procs = Get-WmiObject Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
            foreach ($proc in $procs) {
                $cmdLine = $proc.CommandLine
                if ($cmdLine -and $cmdLine -match [regex]::Escape($ProcessName)) {
                    Write-Log "INFO" "通过命令行匹配到旧进程 PID=$($proc.ProcessId)，正在停止..."
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
    param([string]$ProxyScript)

    $webuiDir = Join-Path $LocalDir "webui"

    $pythonCmd = Get-Command "python3" -ErrorAction SilentlyContinue
    if (-not $pythonCmd) {
        $pythonCmd = Get-Command "python" -ErrorAction SilentlyContinue
    }

    if ($pythonCmd) {
        Write-Log "INFO" "使用 Python 启动 WebUI 代理 (端口 10908 -> 后端 10906)"

        $logFile = Join-Path $LogsDir "webui-proxy.log"
        $startupInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startupInfo.FileName = $pythonCmd.Source
        $startupInfo.Arguments = "-u `"$ProxyScript`""
        $startupInfo.WorkingDirectory = $webuiDir
        $startupInfo.RedirectStandardOutput = $true
        $startupInfo.RedirectStandardError = $true
        $startupInfo.UseShellExecute = $false
        $startupInfo.CreateNoWindow = $true

        $proc = New-Object System.Diagnostics.Process
        $proc.StartInfo = $startupInfo
        $proc.Start() | Out-Null

        $proc.Id | Out-File -FilePath $WebuiPidFile -Encoding UTF8 -Force
        Write-Log "INFO" "Python WebUI 代理已启动，PID=$($proc.Id)"
        return $true
    }

    return $false
}

# ---- 启动 WebUI 代理（PowerShell 内置） ----
function Start-WebuiProxyPowershell {
    param([string]$WebuiDir, [string]$BackendUrl)

    Write-Log "WARN" "未找到 Python，尝试使用 PowerShell 内置 HTTP 代理..."

    $proxyScriptContent = @"
# matrix-webui-proxy.ps1
# PowerShell HTTP 反向代理
`$listener = New-Object System.Net.HttpListener
`$listener.Prefixes.Add("http://+:10908/")
`$listener.Start()
`$backendBase = "$BackendUrl"
while (`$listener.IsListening) {
    try {
        `$context = `$listener.GetContext()
        `$request = `$context.Request
        `$response = `$context.Response
        `$path = `$request.Url.AbsolutePath
        if (`$path -eq "/" -or `$path -eq "") { `$path = "/index.html" }
        if (`$path -like "/api/*") {
            `$backendPath = "$BackendUrl`$path"
            if (`$request.Url.Query -ne "") { `$backendPath = "$BackendUrl`$path`$(`$request.Url.Query)" }
            try {
                `$wc = New-Object System.Net.WebClient
                `$responseData = `$wc.DownloadData(`$backendPath)
                `$response.ContentType = "application/json; charset=utf-8"
                `$response.StatusCode = 200
                `$response.OutputStream.Write(`$responseData, 0, `$responseData.Length)
            } catch {
                `$response.StatusCode = 502
                `$errorMsg = [Text.Encoding]::UTF8.GetBytes('{"error":"Backend unavailable"}')
                `$response.OutputStream.Write(`$errorMsg, 0, `$errorMsg.Length)
            }
        } else {
            `$filePath = Join-Path "$WebuiDir" "dist" `$path.TrimStart('/')
            if (-not (Test-Path `$filePath)) { `$filePath = Join-Path "$WebuiDir" "dist" "index.html" }
            if (Test-Path `$filePath) {
                try {
                    `$fileBytes = [System.IO.File]::ReadAllBytes(`$filePath)
                    `$ext = [System.IO.Path]::GetExtension(`$filePath)
                    switch (`$ext) {
                        '.html' { `$response.ContentType = 'text/html; charset=utf-8' }
                        '.js'   { `$response.ContentType = 'application/javascript; charset=utf-8' }
                        '.css'  { `$response.ContentType = 'text/css; charset=utf-8' }
                        '.png'  { `$response.ContentType = 'image/png' }
                        '.jpg'  { `$response.ContentType = 'image/jpeg' }
                        '.svg'  { `$response.ContentType = 'image/svg+xml' }
                        '.ico'  { `$response.ContentType = 'image/x-icon' }
                        '.json' { `$response.ContentType = 'application/json; charset=utf-8' }
                        default { `$response.ContentType = 'application/octet-stream' }
                    }
                    `$response.StatusCode = 200
                    `$response.OutputStream.Write(`$fileBytes, 0, `$fileBytes.Length)
                } catch { `$response.StatusCode = 500 }
            } else { `$response.StatusCode = 404 }
        }
        `$response.Close()
    } catch { }
}
"@

    $proxyScriptPath = Join-Path $PidDir "matrix-webui-proxy.ps1"
    $proxyScriptContent | Out-File -FilePath $proxyScriptPath -Encoding UTF8 -Force

    try {
        $proc = Start-Process -FilePath "powershell.exe" `
            -ArgumentList "-ExecutionPolicy Bypass -WindowStyle Hidden -File `"$proxyScriptPath`"" `
            -PassThru -NoNewWindow:$false

        if ($proc -and $proc.Id) {
            $proc.Id | Out-File -FilePath $WebuiPidFile -Encoding UTF8 -Force
            Write-Log "INFO" "PowerShell 代理已启动 (端口 10908)，PID=$($proc.Id)"
            return $true
        }
    } catch {
        Write-Log "ERROR" "PowerShell 代理启动失败: $_"
    }

    return $false
}

# ---- 启动 WebUI 代理（主入口） ----
function Start-WebuiProxy {
    param([string]$ProxyScript)

    $webuiDir = Join-Path $LocalDir "webui"
    $backendUrl = "http://localhost:10906"

    # 优先尝试 Python
    if (Start-WebuiProxyPython -ProxyScript $ProxyScript) {
        return $true
    }

    # Python 失败，尝试 PowerShell 内置代理
    Write-Log "WARN" "Python 不可用，尝试 PowerShell 内置代理..."
    if (Start-WebuiProxyPowershell -WebuiDir $webuiDir -BackendUrl $backendUrl) {
        return $true
    }

    # 都失败，提示安装 Python
    Write-Log "ERROR" "未能启动 WebUI 代理。"
    Write-Log "ERROR" "请安装 Python 3 后重试。"
    Write-Log "INFO" "可以从 https://www.python.org/downloads/ 下载安装"
    Write-Log "INFO" "安装后重新运行 start.ps1 即可"
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
$javaExe = Join-Path $jdkHome "bin" "java.exe"

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
# 也停止 WebUI 代理相关进程
$webuiPidFileContent = $null
if (Test-Path $WebuiPidFile) {
    $webuiPidFileContent = Get-Content $WebuiPidFile -Raw | ForEach-Object { $_.Trim() }
}
try {
    $pyProcs = Get-CimInstance Win32_Process -Filter "Name='python.exe' OR Name='python3.exe'" -ErrorAction SilentlyContinue
    foreach ($proc in $pyProcs) {
        $cmdLine = $proc.CommandLine
        if ($cmdLine -and ($cmdLine -match "proxy_server" -or ($webuiPidFileContent -and $proc.ProcessId -eq $webuiPidFileContent))) {
            Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
        }
    }
} catch { }
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
$serviceLogFile = Join-Path $LogsDir "matrix-local.log"
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
        Write-Log "ERROR" "后端服务启动后立即退出，请检查日志: $serviceLogFile"
        exit 1
    }
} catch {
    Write-Log "ERROR" "启动后端服务失败: $_"
    exit 1
}

# 9. 启动 WebUI 代理
$proxyScript = Join-Path $BinDir "proxy_server.py"
if (Test-Path $proxyScript) {
    Start-WebuiProxy -ProxyScript $proxyScript
} else {
    Write-Log "WARN" "未找到 proxy_server.py，尝试使用 PowerShell 内置代理"
    $webuiDir = Join-Path $LocalDir "webui"
    Start-WebuiProxyPowershell -WebuiDir $webuiDir -BackendUrl "http://localhost:10906"
}

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix 本地服务启动完成"
Write-Log "INFO" "=========================================="
Write-Log "INFO" "后端服务: http://localhost:$serverPort"
Write-Log "INFO" "WebUI:    http://localhost:10908"
Write-Log "INFO" ""
Write-Log "INFO" "使用 'matrix stop' 停止服务"
