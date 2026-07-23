<#
.SYNOPSIS
    Matrix 本地服务一键安装脚本 (Windows)
.DESCRIPTION
    自动检测系统环境、下载 Matrix 本地服务、JDK 21、WebUI 并完成安装
.NOTES
    对应 install.sh 的 PowerShell 实现
    版本: 1.0.2
    需要 PowerShell 5.1 或更高版本
    建议以管理员身份运行（非必须，但可以避免权限问题）
#>

# ==========================================
# 初始化
# ==========================================

# 编码设置 - UTF-8 with BOM, PowerShell 5.1 兼容
$OutputEncoding = [Text.Encoding]::UTF8
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# 脚本路径
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# 安装目录
$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$JdksDir = Join-Path $MatrixHome "jdk21"
$CliDir = Join-Path $env:USERPROFILE ".local" "bin"

# 子目录
$BinDir = Join-Path $LocalDir "bin"
$DataDir = Join-Path $LocalDir "data"
$ConfigDir = Join-Path $LocalDir "config"
$SettingsDir = Join-Path $LocalDir "settings"
$LogsDir = Join-Path $LocalDir "logs"
$WebuiDir = Join-Path $LocalDir "webui"
$TmpDir = Join-Path $LocalDir "tmp"

$SubDirs = @($BinDir, $DataDir, $ConfigDir, $SettingsDir, $LogsDir, $WebuiDir, $TmpDir)

# ---- 日志函数 ----
function Write-Log {
    param([string]$Level = "INFO", [string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

# ---- 错误处理 ----
$ErrorActionPreference = "Stop"

function Exit-WithError {
    param([string]$Message)
    Write-Log "ERROR" $Message
    Write-Log "INFO" "安装失败，请检查上述错误信息后重试"
    pause
    exit 1
}

# ==========================================
# 版本信息读取
# ==========================================

# 从当前目录读取 latest-version.txt
$VersionFile = Join-Path $ScriptDir "latest-version.txt"
if (-not (Test-Path $VersionFile)) {
    Exit-WithError "未找到版本信息文件: $VersionFile"
}

# 手动解析 key=value 格式，支持内嵌变量展开
$ConfigTable = @{}

function Parse-VersionFile {
    param([string]$FilePath)

    $lines = Get-Content $FilePath -Encoding UTF8 -ErrorAction Stop
    $result = @{}

    # 第一遍：解析所有 key=value
    foreach ($line in $lines) {
        $trimmedLine = $line.Trim()
        if ($trimmedLine -eq "" -or $trimmedLine.StartsWith("#")) {
            continue
        }
        $eqIndex = $trimmedLine.IndexOf("=")
        if ($eqIndex -gt 0) {
            $key = $trimmedLine.Substring(0, $eqIndex).Trim()
            $value = $trimmedLine.Substring($eqIndex + 1).Trim()
            $result[$key] = $value
        }
    }

    return $result
}

function Resolve-VariableRefs {
    param(
        [hashtable]$Config,
        [string]$Value
    )
    # 递归展开 ${VAR_NAME} 引用
    $maxIterations = 10
    for ($i = 0; $i -lt $maxIterations; $i++) {
        $matches = [regex]::Matches($Value, '\$\{(\w+)\}')
        if ($matches.Count -eq 0) {
            break
        }
        foreach ($match in $matches) {
            $varName = $match.Groups[1].Value
            if ($Config.ContainsKey($varName)) {
                $Value = $Value.Replace($match.Groups[0].Value, $Config[$varName])
            }
        }
    }
    return $Value
}

# 解析版本文件
$RawConfig = Parse-VersionFile -FilePath $VersionFile

# 展开所有变量引用（需要多次迭代，因为变量可能嵌套引用）
for ($i = 0; $i -lt 20; $i++) {
    $changed = $false
    foreach ($key in $RawConfig.Keys) {
        $oldValue = $RawConfig[$key]
        $newValue = Resolve-VariableRefs -Config $RawConfig -Value $oldValue
        if ($newValue -ne $oldValue) {
            $RawConfig[$key] = $newValue
            $changed = $true
        }
    }
    if (-not $changed) {
        break
    }
}

$ConfigTable = $RawConfig

$MATRIX_VERSION = $ConfigTable["MATRIX_VERSION"]
$RELEASE_TAG = $ConfigTable["RELEASE_TAG"]
$RAW_BASE = $ConfigTable["RAW_BASE"]
$RELEASE_BASE = $ConfigTable["RELEASE_BASE"]
$JAR_FILE_NAME = $ConfigTable["JAR_FILE_NAME"]
$JAR_PART_Z01 = $ConfigTable["JAR_PART_Z01"]
$JAR_PART_ZIP = $ConfigTable["JAR_PART_ZIP"]
$WEBUI_ZIP_NAME = $ConfigTable["WEBUI_ZIP_NAME"]

# 检测架构选择 JDK
$ARCH = $env:PROCESSOR_ARCHITECTURE
if ($ARCH -eq "AMD64") {
    $JDK_URL = $ConfigTable["JDK_URL_WIN_X86_64"]
    $JDK_FILENAME = $ConfigTable["JDK_FILENAME_WIN_X86_64"]
} elseif ($ARCH -eq "ARM64") {
    $JDK_URL = $ConfigTable["JDK_URL_WIN_ARM64"]
    $JDK_FILENAME = $ConfigTable["JDK_FILENAME_WIN_ARM64"]
} else {
    Exit-WithError "不支持的 CPU 架构: $ARCH (仅支持 AMD64/x64 和 ARM64)"
}

# ==========================================
# 工具函数
# ==========================================

# ---- 下载文件（带重试） ----
function Download-File {
    param(
        [string]$Url,
        [string]$Destination,
        [int]$Retries = 3,
        [string]$Label = ""
    )

    if ($Label -eq "") {
        $Label = [System.IO.Path]::GetFileName($Destination)
    }

    for ($i = 1; $i -le $Retries; $i++) {
        try {
            Write-Log "INFO" "正在下载 $Label ..."
            Write-Log "DEBUG" "URL: $Url"
            Write-Log "DEBUG" "目标: $Destination"

            # 使用 WebClient 避免 Invoke-WebRequest 在 PS5.1 的编码问题
            $wc = New-Object System.Net.WebClient
            $wc.Headers.Add("User-Agent", "Matrix-Installer/1.0.2")
            $wc.DownloadFile($Url, $Destination)
            $wc.Dispose()

            if (Test-Path $Destination) {
                $fileSize = (Get-Item $Destination).Length
                Write-Log "INFO" "下载完成: $Label ($fileSize bytes)"
                return $true
            }
        } catch {
            Write-Log "WARN" "下载失败 (第 $i 次/$Retries): $_"
            if ($i -lt $Retries) {
                $waitTime = $i * 3
                Write-Log "INFO" "等待 ${waitTime} 秒后重试..."
                Start-Sleep -Seconds $waitTime
            }
        }
    }

    return $false
}

# ---- 创建目录 ----
function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
        Write-Log "INFO" "创建目录: $Path"
    }
}

# ---- 下载并解压 zip ----
function Download-And-Extract {
    param(
        [string]$Url,
        [string]$ZipPath,
        [string]$ExtractDir,
        [string]$Label
    )

    if (-not (Download-File -Url $Url -Destination $ZipPath -Label $Label)) {
        return $false
    }

    try {
        Write-Log "INFO" "正在解压 $Label ..."
        # 先删除目标目录内容
        if (Test-Path $ExtractDir) {
            Remove-Item -Path "$ExtractDir\*" -Recurse -Force -ErrorAction SilentlyContinue
        } else {
            New-Item -ItemType Directory -Path $ExtractDir -Force | Out-Null
        }
        Expand-Archive -Path $ZipPath -DestinationPath $ExtractDir -Force
        Write-Log "INFO" "解压完成: $ExtractDir"
        return $true
    } catch {
        Write-Log "ERROR" "解压失败: $_"
        return $false
    }
}

# ==========================================
# 系统检测
# ==========================================

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  Matrix 本地服务安装 (Windows)"
Write-Log "INFO" "  版本: $MATRIX_VERSION"
Write-Log "INFO" "=========================================="
Write-Log "INFO" ""

# 检测系统
$osInfo = Get-WmiObject Win32_OperatingSystem -ErrorAction SilentlyContinue
if ($osInfo) {
    Write-Log "INFO" "系统: $($osInfo.Caption) $($osInfo.Version)"
} else {
    Write-Log "INFO" "系统: Windows (无法获取详细信息)"
}
Write-Log "INFO" "架构: $ARCH"
Write-Log "INFO" "用户: $env:USERNAME"
Write-Log "INFO" "安装目录: $LocalDir"
Write-Log "INFO" ""

# 检测 PowerShell 版本
$psVersion = $PSVersionTable.PSVersion.Major
$psVersionStr = "$($PSVersionTable.PSVersion.Major).$($PSVersionTable.PSVersion.Minor)"
if ($psVersion -lt 5) {
    Exit-WithError "需要 PowerShell 5.0 或更高版本，当前版本: $psVersionStr"
}
Write-Log "INFO" "PowerShell 版本: $psVersionStr"

# ==========================================
# 配置 API Key
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  配置 API Key"
Write-Log "INFO" "------------------------------------------"

$apiKeyPrompt = Read-Host "请输入你的 DeepSeek API Key (输入后按回车)"

# 写入环境变量（用户级别）
try {
    [Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", $apiKeyPrompt, "User")
    Write-Log "INFO" "DEEPSEEK_API_KEY 已写入用户环境变量"
} catch {
    Write-Log "WARN" "写入环境变量失败: $_"
    Write-Log "WARN" "请手动设置环境变量 DEEPSEEK_API_KEY"
}

# ==========================================
# 创建目录结构
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  创建目录结构"
Write-Log "INFO" "------------------------------------------"

Ensure-Directory -Path $MatrixHome
foreach ($dir in $SubDirs) {
    Ensure-Directory -Path $dir
}

# ==========================================
# 下载核心文件
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载核心文件"
Write-Log "INFO" "------------------------------------------"

$JarUrl = "$RELEASE_BASE/$RELEASE_TAG/$JAR_FILE_NAME"
$JarPath = Join-Path $LocalDir $JAR_FILE_NAME

# 尝试直接下载完整 JAR
$jarDownloaded = Download-File -Url $JarUrl -Destination $JarPath -Label "matrix-local JAR"

# 完整 JAR 下载失败，尝试分卷下载
if (-not $jarDownloaded) {
    Write-Log "WARN" "完整 JAR 下载失败，尝试分卷下载..."
    Write-Log "INFO" "需要安装 7-Zip 或 WinRAR 来合并分卷文件"

    $partZ01Url = "$RELEASE_BASE/$RELEASE_TAG/$JAR_PART_Z01"
    $partZipUrl = "$RELEASE_BASE/$RELEASE_TAG/$JAR_PART_ZIP"
    $partZ01Path = Join-Path $TmpDir $JAR_PART_Z01
    $partZipPath = Join-Path $TmpDir $JAR_PART_ZIP

    $z01Ok = Download-File -Url $partZ01Url -Destination $partZ01Path -Label "JAR 分卷 (1/2)"
    $zipOk = Download-File -Url $partZipUrl -Destination $partZipPath -Label "JAR 分卷 (2/2)"

    if ($z01Ok -and $zipOk) {
        Write-Log "INFO" "分卷下载完成，正在合并..."

        # 尝试几种合并方式
        $mergedOk = $false

        # 方式1: 使用 copy 命令合并（适用于 7-Zip 分卷）
        try {
            $combinedPath = Join-Path $TmpDir "combined.zip"
            # 先将 .z01 复制到 combined.zip
            Copy-Item -Path $partZ01Path -Destination $combinedPath -Force
            # 追加 .zip 内容
            $zipBytes = [System.IO.File]::ReadAllBytes($partZipPath)
            $stream = [System.IO.File]::OpenWrite($combinedPath)
            $stream.Seek(0, [System.IO.SeekOrigin]::End) | Out-Null
            $stream.Write($zipBytes, 0, $zipBytes.Length)
            $stream.Close()
            Write-Log "INFO" "分卷合并完成: $combinedPath"

            # 解压合并后的文件
            Expand-Archive -Path $combinedPath -DestinationPath $TmpDir -Force
            $extractedJar = Join-Path $TmpDir $JAR_FILE_NAME
            if (Test-Path $extractedJar) {
                Copy-Item -Path $extractedJar -Destination $JarPath -Force
                $mergedOk = $true
                Write-Log "INFO" "JAR 合并解压成功"
            }
        } catch {
            Write-Log "WARN" "合并方式1失败: $_"
        }

        if (-not $mergedOk) {
            # 方式2: 提示用户手动合并
            Write-Log "ERROR" "自动合并失败，请手动执行以下命令："
            Write-Log "INFO" "  1. 打开目录: $TmpDir"
            Write-Log "INFO" "  2. 使用 7-Zip 打开 $JAR_PART_ZIP"
            Write-Log "INFO" "  3. 解压得到 $JAR_FILE_NAME"
            Write-Log "INFO" "  4. 复制到: $JarPath"
            Exit-WithError "JAR 文件下载/合并失败，请手动处理"
        }
    } else {
        Exit-WithError "JAR 分卷下载失败"
    }
}

# ==========================================
# 下载 bin/ 脚本
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载 bin/ 脚本"
Write-Log "INFO" "------------------------------------------"

# PS1 版本下载 .ps1 文件替代 .sh 文件
$BinFiles = @(
    "proxy_server.py",
    "start.ps1",
    "stop.ps1",
    "restart.ps1"
)

foreach ($file in $BinFiles) {
    $url = "$RAW_BASE/$RELEASE_TAG/install/bin/$file"
    $dest = Join-Path $BinDir $file
    if (-not (Download-File -Url $url -Destination $dest -Label $file)) {
        Write-Log "WARN" "下载 $file 失败，将尝试在安装后手动处理"
    }
}

# ==========================================
# 下载 config/ 配置（跳过已存在文件）
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载 config/ 配置"
Write-Log "INFO" "------------------------------------------"

$ConfigFiles = @(
    "application.yml",
    "banner.txt"
)

foreach ($file in $ConfigFiles) {
    $dest = Join-Path $ConfigDir $file
    if (Test-Path $dest) {
        Write-Log "INFO" "跳过 $file (已存在)"
        continue
    }
    $url = "$RAW_BASE/$RELEASE_TAG/install/config/$file"
    if (-not (Download-File -Url $url -Destination $dest -Label "config/$file")) {
        Write-Log "WARN" "下载 config/$file 失败"
    }
}

# ==========================================
# 下载 data/
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载 data/"
Write-Log "INFO" "------------------------------------------"

$dataUrl = "$RAW_BASE/$RELEASE_TAG/install/data/schema.sql"
$dataDest = Join-Path $DataDir "schema.sql"
if (Test-Path $dataDest) {
    Write-Log "INFO" "跳过 schema.sql (已存在)"
} else {
    if (-not (Download-File -Url $dataUrl -Destination $dataDest -Label "data/schema.sql")) {
        Write-Log "WARN" "下载 data/schema.sql 失败"
    }
}

# ==========================================
# 下载 settings/
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载 settings/"
Write-Log "INFO" "------------------------------------------"

$SettingsFiles = @(
    "MEMORY.md",
    "risk-level.yml"
)

foreach ($file in $SettingsFiles) {
    $dest = Join-Path $SettingsDir $file
    if (Test-Path $dest) {
        Write-Log "INFO" "跳过 $file (已存在)"
        continue
    }
    $url = "$RAW_BASE/$RELEASE_TAG/install/settings/$file"
    if (-not (Download-File -Url $url -Destination $dest -Label "settings/$file")) {
        Write-Log "WARN" "下载 settings/$file 失败"
    }
}

# 下载 settings/skill/ 目录
$SkillBaseUrl = "$RAW_BASE/$RELEASE_TAG/install/settings/skill"
$SkillDestDir = Join-Path $SettingsDir "skill"
Ensure-Directory -Path $SkillDestDir

$SkillFiles = @(
    "hiot-control-1.0.0",
    "pdf",
    "query-typhoon",
    "mac_scan_pdf"
)

foreach ($skill in $SkillFiles) {
    $dest = Join-Path $SkillDestDir $skill
    if (Test-Path $dest) {
        Write-Log "INFO" "跳过 skill/$skill (已存在)"
        continue
    }
    $url = "$SkillBaseUrl/$skill"
    Ensure-Directory -Path $dest
    if (-not (Download-File -Url $url -Destination (Join-Path $dest "SKILL.md") -Label "skill/$skill/SKILL.md")) {
        Write-Log "WARN" "下载 skill/$skill 失败"
    }
}

# ==========================================
# 下载 WebUI
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载 WebUI"
Write-Log "INFO" "------------------------------------------"

$WebuiZipUrl = "$RELEASE_BASE/$RELEASE_TAG/$WEBUI_ZIP_NAME"
$WebuiZipPath = Join-Path $TmpDir $WEBUI_ZIP_NAME

if (Download-And-Extract -Url $WebuiZipUrl -ZipPath $WebuiZipPath -ExtractDir $WebuiDir -Label "WebUI ($WEBUI_ZIP_NAME)") {
    Write-Log "INFO" "WebUI 安装完成"
} else {
    Exit-WithError "WebUI 下载/解压失败"
}

# ==========================================
# 下载 JDK 21
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载 JDK 21"
Write-Log "INFO" "------------------------------------------"

$JdkZipPath = Join-Path $TmpDir $JDK_FILENAME

if (Test-Path (Join-Path $JdksDir "bin" "java.exe")) {
    Write-Log "INFO" "JDK 21 已安装，跳过下载"
} else {
    if (Download-File -Url $JDK_URL -Destination $JdkZipPath -Label "JDK 21 ($JDK_FILENAME)") {
        Write-Log "INFO" "JDK 下载完成，正在解压..."
        try {
            Ensure-Directory -Path $JdksDir
            Expand-Archive -Path $JdkZipPath -DestinationPath $JdksDir -Force

            # 处理嵌套目录：解压后可能有一个外层目录 jdk-21.x.x
            $subDirs = Get-ChildItem -Path $JdksDir -Directory
            if ($subDirs -and $subDirs.Count -eq 1 -and $subDirs[0].Name -like "jdk-21*") {
                $extractedDir = $subDirs[0].FullName
                Write-Log "INFO" "检测到嵌套目录，正在展开: $($subDirs[0].Name)"
                Get-ChildItem -Path $extractedDir | Move-Item -Destination $JdksDir -Force
                Remove-Item -Path $extractedDir -Recurse -Force
            }

            if (Test-Path (Join-Path $JdksDir "bin" "java.exe")) {
                Write-Log "INFO" "JDK 21 安装完成: $JdksDir"
            } else {
                Write-Log "WARN" "JDK 解压后未找到 java.exe，请检查目录结构: $JdksDir"
                Get-ChildItem -Path $JdksDir -Depth 2 | ForEach-Object { Write-Log "DEBUG" $_.FullName }
            }
        } catch {
            Exit-WithError "JDK 解压失败: $_"
        }
    } else {
        Exit-WithError "JDK 21 下载失败"
    }
}

# ==========================================
# 安装 matrix CLI
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  安装 matrix CLI"
Write-Log "INFO" "------------------------------------------"

Ensure-Directory -Path $CliDir

# 创建 matrix.ps1 CLI 脚本
$MatrixCliPath = Join-Path $CliDir "matrix.ps1"

$matrixCliContent = @'
<#
.SYNOPSIS
    Matrix 本地服务命令行工具
.DESCRIPTION
    管理 Matrix 本地服务的启动、停止、重启、状态检查等
    使用方法: matrix <command>
.NOTES
    由 install.ps1 自动生成
    版本: 1.0.2
#>

param(
    [Parameter(Position=0)]
    [ValidateSet("start", "stop", "restart", "status", "logs", "webui-logs", "update", "uninstall", "help")]
    [string]$Command = "help"
)

$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$BinDir = Join-Path $LocalDir "bin"
$LogsDir = Join-Path $LocalDir "logs"

function Write-Log {
    param([string]$Level = "INFO", [string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

switch ($Command) {
    "start" {
        $startScript = Join-Path $BinDir "start.ps1"
        if (Test-Path $startScript) {
            & $startScript
        } else {
            Write-Log "ERROR" "未找到 start.ps1，请重新安装"
        }
    }
    "stop" {
        $stopScript = Join-Path $BinDir "stop.ps1"
        if (Test-Path $stopScript) {
            & $stopScript
        } else {
            Write-Log "ERROR" "未找到 stop.ps1，请重新安装"
        }
    }
    "restart" {
        $restartScript = Join-Path $BinDir "restart.ps1"
        if (Test-Path $restartScript) {
            & $restartScript
        } else {
            Write-Log "ERROR" "未找到 restart.ps1，请重新安装"
        }
    }
    "status" {
        $servicePidFile = Join-Path $LocalDir "tmp" "matrix-local.pid"
        $webuiPidFile = Join-Path $LocalDir "tmp" "matrix-webui.pid"

        Write-Log "INFO" "Matrix 本地服务状态:"
        Write-Log "INFO" ""

        if (Test-Path $servicePidFile) {
            $pid = Get-Content $servicePidFile -Raw | ForEach-Object { $_.Trim() }
            $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Log "INFO" "  后端服务: 运行中 (PID=$pid)"
            } else {
                Write-Log "INFO" "  后端服务: 未运行 (PID 文件过期)"
            }
        } else {
            Write-Log "INFO" "  后端服务: 未运行"
        }

        if (Test-Path $webuiPidFile) {
            $pid = Get-Content $webuiPidFile -Raw | ForEach-Object { $_.Trim() }
            $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Log "INFO" "  WebUI 代理: 运行中 (PID=$pid)"
            } else {
                Write-Log "INFO" "  WebUI 代理: 未运行 (PID 文件过期)"
            }
        } else {
            Write-Log "INFO" "  WebUI 代理: 未运行"
        }

        # 检查端口
        try {
            $serviceCheck = netstat -an | Select-String "127.0.0.1:10906"
            if ($serviceCheck) {
                Write-Log "INFO" "  端口 10906 (后端): 已监听"
            } else {
                Write-Log "INFO" "  端口 10906 (后端): 未监听"
            }

            $webuiCheck = netstat -an | Select-String "127.0.0.1:10908"
            if ($webuiCheck) {
                Write-Log "INFO" "  端口 10908 (WebUI): 已监听"
            } else {
                Write-Log "INFO" "  端口 10908 (WebUI): 未监听"
            }
        } catch {
            Write-Log "WARN" "端口检查失败 (需要管理员权限)"
        }
    }
    "logs" {
        $logFile = Join-Path $LogsDir "matrix-local.log"
        if (Test-Path $logFile) {
            Get-Content $logFile -Tail 50 -Wait
        } else {
            Write-Log "WARN" "日志文件不存在: $logFile"
        }
    }
    "webui-logs" {
        $logFile = Join-Path $LogsDir "webui-proxy.log"
        if (Test-Path $logFile) {
            Get-Content $logFile -Tail 50 -Wait
        } else {
            Write-Log "WARN" "日志文件不存在: $logFile"
        }
    }
    "update" {
        Write-Log "INFO" "正在检查更新..."
        # 重新下载 install.ps1 并运行
        $tmpScript = Join-Path $env:TEMP "matrix-update-install.ps1"
        try {
            $wc = New-Object System.Net.WebClient
            # 需要从版本文件获取最新版本信息
            # 简化处理：提示用户手动运行 install.ps1
            Write-Log "INFO" "请运行以下命令重新安装最新版本:"
            Write-Log "INFO" "  powershell -ExecutionPolicy Bypass -File install.ps1"
        } catch {
            Write-Log "ERROR" "更新检查失败: $_"
        }
    }
    "uninstall" {
        Write-Log "WARN" "此操作将停止服务并删除 $LocalDir 目录"
        $confirm = Read-Host "确认卸载? (y/N)"
        if ($confirm -eq "y" -or $confirm -eq "Y") {
            # 先停止服务
            $stopScript = Join-Path $BinDir "stop.ps1"
            if (Test-Path $stopScript) {
                & $stopScript
            }
            Start-Sleep -Seconds 2

            try {
                Remove-Item -Path $LocalDir -Recurse -Force
                Write-Log "INFO" "已删除: $LocalDir"
            } catch {
                Write-Log "ERROR" "删除失败: $_"
            }

            # 删除 CLI
            $cliPath = Join-Path $env:USERPROFILE ".local" "bin" "matrix.ps1"
            if (Test-Path $cliPath) {
                Remove-Item -Path $cliPath -Force
                Write-Log "INFO" "已删除 CLI: $cliPath"
            }

            Write-Log "INFO" "卸载完成"
        } else {
            Write-Log "INFO" "取消卸载"
        }
    }
    "help" {
        Write-Host ""
        Write-Host "Matrix 本地服务命令行工具"
        Write-Host ""
        Write-Host "使用方法: matrix <command>"
        Write-Host ""
        Write-Host "可用命令:"
        Write-Host "  start        启动 Matrix 本地服务"
        Write-Host "  stop         停止 Matrix 本地服务"
        Write-Host "  restart      重启 Matrix 本地服务"
        Write-Host "  status       查看服务运行状态"
        Write-Host "  logs         查看后端服务日志 (实时跟踪)"
        Write-Host "  webui-logs   查看 WebUI 代理日志 (实时跟踪)"
        Write-Host "  update       检查并更新到最新版本"
        Write-Host "  uninstall    卸载 Matrix 本地服务"
        Write-Host "  help         显示此帮助信息"
        Write-Host ""
    }
}
'@

$matrixCliContent | Out-File -FilePath $MatrixCliPath -Encoding UTF8 -Force
Write-Log "INFO" "已创建 CLI: $MatrixCliPath"

# ==========================================
# 配置 PATH
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  配置 PATH 环境变量"
Write-Log "INFO" "------------------------------------------"

$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($currentPath -notlike "*$CliDir*") {
    try {
        $newPath = "$CliDir;$currentPath"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
        Write-Log "INFO" "已将 $CliDir 添加到用户 PATH 环境变量"
        # 更新当前会话的 PATH
        $env:Path = "$CliDir;$env:Path"
    } catch {
        Write-Log "WARN" "PATH 配置失败: $_"
        Write-Log "INFO" "请手动将以下目录添加到 PATH:"
        Write-Log "INFO" "  $CliDir"
    }
} else {
    Write-Log "INFO" "$CliDir 已在 PATH 中"
}

# ==========================================
# 清理临时文件
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  清理临时文件"
Write-Log "INFO" "------------------------------------------"

# 清理下载的临时文件（保留 JAR 和必要的文件）
$tempFilesToClean = @(
    (Join-Path $TmpDir $JAR_PART_Z01),
    (Join-Path $TmpDir $JAR_PART_ZIP),
    (Join-Path $TmpDir "combined.zip"),
    (Join-Path $TmpDir $WEBUI_ZIP_NAME),
    (Join-Path $TmpDir $JDK_FILENAME)
)

foreach ($f in $tempFilesToClean) {
    if (Test-Path $f) {
        Remove-Item -Path $f -Force -ErrorAction SilentlyContinue
        Write-Log "INFO" "已清理: $([System.IO.Path]::GetFileName($f))"
    }
}

# ==========================================
# 启动服务
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  安装完成，启动服务"
Write-Log "INFO" "------------------------------------------"

Write-Log "INFO" "Matrix 本地服务已安装到: $LocalDir"
Write-Log "INFO" ""
Write-Log "INFO" "是否立即启动服务?"
$startConfirm = Read-Host "启动服务? (Y/n)"

if ($startConfirm -ne "n" -and $startConfirm -ne "N") {
    $startScript = Join-Path $BinDir "start.ps1"
    if (Test-Path $startScript) {
        Write-Log "INFO" "正在启动服务..."
        & $startScript
    } else {
        Write-Log "ERROR" "未找到 start.ps1，请手动运行 install.ps1 重新安装"
    }
} else {
    Write-Log "INFO" "安装完成。手动启动:"
    Write-Log "INFO" "  matrix start"
    Write-Log "INFO" "  或"
    Write-Log "INFO" "  powershell -ExecutionPolicy Bypass -File `"$BinDir\start.ps1`""
}

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  安装完成！"
Write-Log "INFO" "=========================================="
Write-Log "INFO" ""
Write-Log "INFO" "WebUI 访问地址: http://localhost:10908"
Write-Log "INFO" "后端 API 地址:  http://localhost:10906"
Write-Log "INFO" ""
Write-Log "INFO" "常用命令:"
Write-Log "INFO" "  matrix start      启动服务"
Write-Log "INFO" "  matrix stop       停止服务"
Write-Log "INFO" "  matrix restart    重启服务"
Write-Log "INFO" "  matrix status     查看状态"
Write-Log "INFO" "  matrix logs       查看日志"
Write-Log "INFO" ""

pause
