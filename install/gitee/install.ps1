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
$CliDir = Join-Path (Join-Path $env:USERPROFILE ".local") "bin"

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

# 从当前目录读取 latest-version.txt，若不存在则从 Gitee 下载
$VersionFile = Join-Path $ScriptDir "latest-version.txt"
if (-not (Test-Path $VersionFile)) {
    Write-Host "[INFO] 未找到本地版本文件，正在从 Gitee 下载..."
    $versionUrl = "https://gitee.com/za96421955/matrix/raw/latest/install/gitee/latest-version.txt"
    try {
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("User-Agent", "Matrix-Installer/1.0.2")
        $wc.DownloadFile($versionUrl, $VersionFile)
        $wc.Dispose()
        Write-Host "[INFO] 版本信息文件下载完成"
    } catch {
        Exit-WithError "无法下载版本信息文件: $_"
    }
}

# 手动解析 key=value 格式，支持内嵌变量展开
$ConfigTable = @{}

function Parse-VersionFile {
    param([string]$FilePath)

    $lines = Get-Content $FilePath -Encoding UTF8 -ErrorAction Stop
    $result = @{}

    foreach ($line in $lines) {
        $trimmedLine = $line.Trim()
        if ($trimmedLine -eq "" -or $trimmedLine.StartsWith("#")) { continue }
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
    param([hashtable]$Config, [string]$Value)
    $maxIterations = 10
    for ($i = 0; $i -lt $maxIterations; $i++) {
        $matches = [regex]::Matches($Value, '\$\{(\w+)\}')
        if ($matches.Count -eq 0) { break }
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

for ($i = 0; $i -lt 20; $i++) {
    $changed = $false
    foreach ($key in @($RawConfig.Keys)) {
        $oldValue = $RawConfig[$key]
        $newValue = Resolve-VariableRefs -Config $RawConfig -Value $oldValue
        if ($newValue -ne $oldValue) {
            $RawConfig[$key] = $newValue
            $changed = $true
        }
    }
    if (-not $changed) { break }
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

# ---- 下载文件（同步，带重试，无进度条，稳定可靠） ----
function Download-File {
    param(
        [string]$Url,
        [string]$Destination,
        [int]$Retries = 3,
        [string]$Label = ""
    )

    if ([string]::IsNullOrEmpty($Label)) {
        $Label = [System.IO.Path]::GetFileName($Destination)
    }

    for ($i = 1; $i -le $Retries; $i++) {
        try {
            Write-Log "INFO" "正在下载 $Label ..."
            $wc = New-Object System.Net.WebClient
            $wc.Headers.Add("User-Agent", "Matrix-Installer/1.0.2")
            $wc.DownloadFile($Url, $Destination)
            $wc.Dispose()

            if (Test-Path $Destination) {
                $fileSize = (Get-Item $Destination).Length
                Write-Log "INFO" "下载完成: $Label ($fileSize bytes)"
                return $true
            }
            Write-Log "WARN" "文件不存在或大小为0，可能下载失败"
        } catch {
            Write-Log "WARN" "下载失败 (第 $i 次/$Retries): $_"
        }

        if ($i -lt $Retries) {
            $waitTime = $i * 3
            Write-Log "INFO" "等待 ${waitTime} 秒后重试..."
            Start-Sleep -Seconds $waitTime
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

$jarDownloaded = Download-File -Url $JarUrl -Destination $JarPath -Label "matrix-local JAR"

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
        $mergedOk = $false

        # 方式1: 7z
        try {
            $7zPaths = @(
                "${env:ProgramFiles}\7-Zip\7z.exe",
                "${env:ProgramFiles(x86)}\7-Zip\7z.exe"
            )
            $7zExe = $null
            if (Get-Command "7z" -ErrorAction SilentlyContinue) { $7zExe = "7z" }
            if (-not $7zExe) {
                foreach ($p in $7zPaths) { if (Test-Path $p) { $7zExe = $p; break } }
            }
            if ($7zExe) {
                Write-Log "INFO" "尝试 7z 解压分卷 ..."
                & $7zExe x "$partZipPath" -o"$TmpDir" -y | Out-Null
                $extractedJar = Join-Path $TmpDir $JAR_FILE_NAME
                if (Test-Path $extractedJar) {
                    Copy-Item -Path $extractedJar -Destination $JarPath -Force
                    $mergedOk = $true
                    Write-Log "INFO" "7z 解压成功"
                }
            }
        } catch {
            Write-Log "WARN" "7z 解压失败: $_"
        }

        # 方式2: cmd copy /B + Expand-Archive
        if (-not $mergedOk) {
            try {
                Write-Log "INFO" "尝试 copy /B 合并分卷 ..."
                $combinedPath = Join-Path $TmpDir "combined.zip"
                & cmd.exe /c "copy /B `"$partZ01Path`" + `"$partZipPath`" `"$combinedPath`" >nul 2>&1"
                if (Test-Path $combinedPath) {
                    Expand-Archive -Path $combinedPath -DestinationPath $TmpDir -Force
                    $extractedJar = Join-Path $TmpDir $JAR_FILE_NAME
                    if (Test-Path $extractedJar) {
                        Copy-Item -Path $extractedJar -Destination $JarPath -Force
                        $mergedOk = $true
                        Write-Log "INFO" "copy /B 合并解压成功"
                    }
                }
            } catch {
                Write-Log "WARN" "copy /B 合并失败: $_"
            }
        }

        # 方式3: PowerShell 二进制合并
        if (-not $mergedOk) {
            try {
                Write-Log "INFO" "尝试 PowerShell 二进制追加合并 ..."
                $combinedPath = Join-Path $TmpDir "combined.zip"
                Copy-Item -Path $partZ01Path -Destination $combinedPath -Force
                $zipBytes = [System.IO.File]::ReadAllBytes($partZipPath)
                $stream = [System.IO.File]::OpenWrite($combinedPath)
                $stream.Seek(0, [System.IO.SeekOrigin]::End) | Out-Null
                $stream.Write($zipBytes, 0, $zipBytes.Length)
                $stream.Close()
                Expand-Archive -Path $combinedPath -DestinationPath $TmpDir -Force
                $extractedJar = Join-Path $TmpDir $JAR_FILE_NAME
                if (Test-Path $extractedJar) {
                    Copy-Item -Path $extractedJar -Destination $JarPath -Force
                    $mergedOk = $true
                    Write-Log "INFO" "PowerShell 合并解压成功"
                }
            } catch {
                Write-Log "WARN" "PowerShell 合并失败: $_"
            }
        }

        if (-not $mergedOk) {
            Write-Log "ERROR" "自动合并失败，请手动下载 JAR 并放置到 $JarPath"
            Exit-WithError "JAR 文件下载/合并失败"
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

$BinFiles = @("proxy_server.py", "start.ps1", "stop.ps1", "restart.ps1")
foreach ($file in $BinFiles) {
    $url = "$RAW_BASE/$RELEASE_TAG/install/bin/$file"
    $dest = Join-Path $BinDir $file
    if (-not (Download-File -Url $url -Destination $dest -Label $file)) {
        Write-Log "WARN" "下载 $file 失败，将尝试在安装后手动处理"
    }
}

# ==========================================
# 下载 config/ 配置
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载 config/ 配置"
Write-Log "INFO" "------------------------------------------"

$ConfigFiles = @("application.yml", "banner.txt")
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

$ApplicationYml = Join-Path $ConfigDir "application.yml"
if (Test-Path $ApplicationYml) {
    $ymlContent = Get-Content $ApplicationYml -Raw
    $ymlContent = $ymlContent -replace 'base-path:.*', "base-path: $LocalDir"
    [System.IO.File]::WriteAllText($ApplicationYml, $ymlContent, [System.Text.UTF8Encoding]::new($false))
    Write-Log "INFO" "base-path 已更新为 $LocalDir"
}

$LatestVersionUrl = "https://gitee.com/za96421955/matrix/raw/latest/install/gitee/latest-version.txt"
$ServerUrlPath = Join-Path $ConfigDir "server.url"
$VersionPath = Join-Path $ConfigDir "version"
[System.IO.File]::WriteAllText($ServerUrlPath, $LatestVersionUrl, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($VersionPath, $MATRIX_VERSION, [System.Text.UTF8Encoding]::new($false))
Write-Log "INFO" "服务器地址和版本号已保存"

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

$SettingsFiles = @("MEMORY.md", "risk-level.yml")
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

$SkillBaseUrl = "$RAW_BASE/$RELEASE_TAG/install/settings/skill"
$SkillDestDir = Join-Path $SettingsDir "skill"
Ensure-Directory -Path $SkillDestDir

$SkillFiles = @("query-typhoon")
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

if (Download-And-Extract -Url $WebuiZipUrl -ZipPath $WebuiZipPath -ExtractDir $WebuiDir -Label "WebUI") {
    $distIndexHtml = Join-Path (Join-Path $WebuiDir "dist") "index.html"
    if (Test-Path $distIndexHtml) {
        Write-Log "INFO" "检测到 dist/ 目录，上移内容..."
        Get-ChildItem -Path (Join-Path $WebuiDir "dist") -Force | Move-Item -Destination $WebuiDir -Force
        Remove-Item -Path (Join-Path $WebuiDir "dist") -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item -Path $WebuiZipPath -Force -ErrorAction SilentlyContinue
        Write-Log "INFO" "WebUI 安装完成"
    } else {
        Write-Log "WARN" "WebUI 解压后未找到 dist/index.html，请检查"
        Remove-Item -Path $WebuiZipPath -Force -ErrorAction SilentlyContinue
    }
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

if (Test-Path (Join-Path (Join-Path $JdksDir "bin") "java.exe")) {
    Write-Log "INFO" "JDK 21 已安装，跳过下载"
} else {
    if (Download-File -Url $JDK_URL -Destination $JdkZipPath -Label "JDK 21 ($JDK_FILENAME)") {
        Write-Log "INFO" "JDK 下载完成，正在解压..."
        try {
            Ensure-Directory -Path $JdksDir
            Expand-Archive -Path $JdkZipPath -DestinationPath $JdksDir -Force

            $subDirs = Get-ChildItem -Path $JdksDir -Directory
            if ($subDirs -and $subDirs.Count -eq 1 -and $subDirs[0].Name -like "jdk-21*") {
                $extractedDir = $subDirs[0].FullName
                Write-Log "INFO" "检测到嵌套目录，正在展开: $($subDirs[0].Name)"
                Get-ChildItem -Path $extractedDir | Move-Item -Destination $JdksDir -Force
                Remove-Item -Path $extractedDir -Recurse -Force
            }

            if (Test-Path (Join-Path (Join-Path $JdksDir "bin") "java.exe")) {
                Write-Log "INFO" "JDK 21 安装完成: $JdksDir"
            } else {
                Write-Log "WARN" "JDK 解压后未找到 java.exe，请检查目录结构"
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
$ConfigDir = Join-Path $LocalDir "config"

function Write-Log {
    param([string]$Level, [string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

function Get-LatestVersionInfo {
    $urlFile = Join-Path $ConfigDir "server.url"
    $versionUrl = if (Test-Path $urlFile) { (Get-Content $urlFile -Raw).Trim() } else { "https://gitee.com/za96421955/matrix/raw/latest/install/gitee/latest-version.txt" }
    try {
        $wc = New-Object System.Net.WebClient
        $content = $wc.DownloadString($versionUrl)
        $result = @{}
        foreach ($line in $content -split "`n") {
            $line = $line.Trim()
            if ($line -match '^([A-Z_]+)=(.+)$') {
                $key = $matches[1]
                $value = $matches[2] -replace '\$\{(\w+)\}', { param($m) $result[$m.Groups[1].Value] }
                $result[$key] = $value
            }
        }
        return $result
    } catch {
        Write-Log "ERROR" "无法获取版本信息: $_"
        return $null
    }
}

function Compare-Versions($v1, $v2) {
    $p1 = $v1.Split('.')
    $p2 = $v2.Split('.')
    for ($i = 0; $i -lt 3; $i++) {
        $a = if ($i -lt $p1.Length) { [int]$p1[$i] } else { 0 }
        $b = if ($i -lt $p2.Length) { [int]$p2[$i] } else { 0 }
        if ($a -gt $b) { return 1 }
        if ($a -lt $b) { return -1 }
    }
    return 0
}

switch ($Command) {
    "start" {
        $script = Join-Path $BinDir "start.ps1"
        if (Test-Path $script) { & $script } else { Write-Log "ERROR" "未找到 start.ps1" }
    }
    "stop" {
        $script = Join-Path $BinDir "stop.ps1"
        if (Test-Path $script) { & $script } else { Write-Log "ERROR" "未找到 stop.ps1" }
    }
    "restart" {
        $script = Join-Path $BinDir "restart.ps1"
        if (Test-Path $script) { & $script } else { Write-Log "ERROR" "未找到 restart.ps1" }
    }
    "status" {
        $servicePid = Join-Path $BinDir "app.pid"
        $webuiPid = Join-Path $BinDir "webui.pid"
        Write-Log "INFO" "Matrix 本地服务状态:"
        if (Test-Path $servicePid) {
            $pid = (Get-Content $servicePid -Raw).Trim()
            if (Get-Process -Id $pid -ErrorAction SilentlyContinue) {
                Write-Log "INFO" "  后端服务: 运行中 (PID=$pid)"
            } else {
                Write-Log "INFO" "  后端服务: 未运行 (PID 文件过期)"
            }
        } else { Write-Log "INFO" "  后端服务: 未运行" }

        if (Test-Path $webuiPid) {
            $pid = (Get-Content $webuiPid -Raw).Trim()
            if (Get-Process -Id $pid -ErrorAction SilentlyContinue) {
                Write-Log "INFO" "  WebUI 代理: 运行中 (PID=$pid)"
            } else {
                Write-Log "INFO" "  WebUI 代理: 未运行 (PID 文件过期)"
            }
        } else { Write-Log "INFO" "  WebUI 代理: 未运行" }

        try {
            $svc = netstat -an | Select-String "127.0.0.1:10906"
            Write-Log "INFO" "  端口 10906 (后端): " + $(if ($svc) { "已监听" } else { "未监听" })
            $web = netstat -an | Select-String "127.0.0.1:10908"
            Write-Log "INFO" "  端口 10908 (WebUI): " + $(if ($web) { "已监听" } else { "未监听" })
        } catch { Write-Log "WARN" "端口检查需要管理员权限" }
    }
    "logs" {
        $f = Join-Path $LogsDir "matrix-local.log"
        if (Test-Path $f) { Get-Content $f -Tail 50 -Wait } else { Write-Log "WARN" "日志文件不存在" }
    }
    "webui-logs" {
        $f = Join-Path $LogsDir "webui-proxy.log"
        if (Test-Path $f) { Get-Content $f -Tail 50 -Wait } else { Write-Log "WARN" "日志文件不存在" }
    }
    "update" {
        Write-Log "INFO" "正在检查更新..."
        $info = Get-LatestVersionInfo
        if (-not $info) { Write-Log "ERROR" "更新失败: 无法获取版本信息"; exit 1 }
        $remoteVer = $info["MATRIX_VERSION"]
        $remoteTag = $info["RELEASE_TAG"]
        $rawBase   = $info["RAW_BASE"]
        $releaseBase = $info["RELEASE_BASE"]
        $jarFinal  = $info["JAR_FILE_NAME"]
        $jarZ01    = $info["JAR_PART_Z01"]
        $jarZip    = $info["JAR_PART_ZIP"]
        $webuiFile = $info["WEBUI_ZIP_NAME"]

        $localVerPath = Join-Path $ConfigDir "version"
        $localVer = if (Test-Path $localVerPath) { (Get-Content $localVerPath -Raw).Trim() } else { "" }

        if ($localVer) {
            $cmp = Compare-Versions $localVer $remoteVer
            if ($cmp -eq 0) { Write-Log "INFO" "已是最新版本 v$remoteVer"; exit 0 }
            if ($cmp -eq 1) { Write-Log "INFO" "本地版本 v$localVer 高于远程 v$remoteVer，无需更新"; exit 0 }
        }

        Write-Log "INFO" "发现新版本 v$remoteVer，开始升级..."
        # 停止服务
        $stopScript = Join-Path $BinDir "stop.ps1"
        if (Test-Path $stopScript) { & $stopScript }
        Start-Sleep -Seconds 2

        $releaseUrl = "$releaseBase/$remoteTag"
        $tmp = Join-Path $LocalDir ".tmp"
        if (!(Test-Path $tmp)) { New-Item -ItemType Directory -Path $tmp -Force | Out-Null }

        # 下载 JAR
        $jarPath = Join-Path $LocalDir $jarFinal
        try {
            $wc = New-Object System.Net.WebClient
            $wc.DownloadFile("$releaseUrl/$jarFinal", $jarPath)
            Write-Log "INFO" "完整 JAR 下载成功"
        } catch {
            Write-Log "WARN" "完整 JAR 失败，尝试分卷..."
            # 分卷下载（同步）
            $z01p = Join-Path $tmp $jarZ01
            $zipp = Join-Path $tmp $jarZip
            try {
                $wc.DownloadFile("$releaseUrl/$jarZ01", $z01p)
                $wc.DownloadFile("$releaseUrl/$jarZip", $zipp)
                # 合并...
                $combined = Join-Path $tmp "combined.zip"
                Copy-Item $z01p $combined
                $bytes = [System.IO.File]::ReadAllBytes($zipp)
                $fs = [System.IO.File]::OpenWrite($combined)
                $fs.Seek(0, [System.IO.SeekOrigin]::End) | Out-Null
                $fs.Write($bytes, 0, $bytes.Length)
                $fs.Close()
                Expand-Archive $combined -DestinationPath $LocalDir -Force
            } catch {
                Write-Log "ERROR" "分卷下载/合并失败"; exit 1
            }
        }

        # 更新 webui
        try {
            $wuiZip = Join-Path $LocalDir $webuiFile
            $wc = New-Object System.Net.WebClient
            $wc.DownloadFile("$releaseUrl/$webuiFile", $wuiZip)
            Expand-Archive $wuiZip -DestinationPath (Join-Path $LocalDir "webui") -Force
            $di = Join-Path (Join-Path $LocalDir "webui") "dist"
            if (Test-Path $di) {
                Get-ChildItem $di -Force | Move-Item -Destination (Join-Path $LocalDir "webui") -Force
                Remove-Item $di -Recurse -Force
            }
            Remove-Item $wuiZip -Force
            Write-Log "INFO" "WebUI 更新完成"
        } catch { Write-Log "WARN" "WebUI 更新失败: $_" }

        # 更新脚本
        foreach ($f in @("start.ps1","stop.ps1","restart.ps1")) {
            try {
                $wc = New-Object System.Net.WebClient
                $wc.DownloadFile("$rawBase/$remoteVer/install/bin/$f", (Join-Path $BinDir $f))
            } catch { Write-Log "WARN" "下载 $f 失败" }
        }
        [System.IO.File]::WriteAllText($localVerPath, $remoteVer)
        Write-Log "INFO" "升级完成，重启服务..."
        $startScript = Join-Path $BinDir "start.ps1"
        if (Test-Path $startScript) { & $startScript }
    }
    "uninstall" {
        Write-Log "WARN" "此操作将删除 $LocalDir"
        $confirm = Read-Host "确认卸载? (y/N)"
        if ($confirm -eq 'y') {
            $stopScript = Join-Path $BinDir "stop.ps1"
            if (Test-Path $stopScript) { & $stopScript }
            Start-Sleep -Seconds 2
            Remove-Item $LocalDir -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item (Join-Path $CliDir "matrix.ps1") -Force -ErrorAction SilentlyContinue
            Remove-Item (Join-Path $CliDir "matrix.bat") -Force -ErrorAction SilentlyContinue
            Write-Log "INFO" "卸载完成"
        }
    }
    "help" {
        Write-Host "`nMatrix 本地服务命令行工具`n"
        Write-Host "使用方法: matrix <command>`n"
        Write-Host "命令: start, stop, restart, status, logs, webui-logs, update, uninstall, help`n"
    }
}
'@

$matrixCliContent | Out-File -FilePath $MatrixCliPath -Encoding UTF8 -Force
Write-Log "INFO" "已创建 CLI: $MatrixCliPath"

# 创建 matrix.bat 包装器（使 matrix 命令可在 cmd/PowerShell 中直接调用）
$MatrixBatPath = Join-Path $CliDir "matrix.bat"
@"
@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0matrix.ps1" %*
"@ | Out-File -FilePath $MatrixBatPath -Encoding ASCII -Force
Write-Log "INFO" "已创建 CLI 启动器: $MatrixBatPath"

# ==========================================
# 配置 PATH
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  配置 PATH 环境变量"
Write-Log "INFO" "------------------------------------------"

$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($currentPath -notlike "*$CliDir*") {
    try {
        [Environment]::SetEnvironmentVariable("Path", "$CliDir;$currentPath", "User")
        $env:Path = "$CliDir;$env:Path"
        Write-Log "INFO" "已将 $CliDir 添加到用户 PATH"
    } catch {
        Write-Log "WARN" "PATH 配置失败，请手动添加: $CliDir"
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

@($JdkZipPath, $WebuiZipPath, (Join-Path $TmpDir $JAR_PART_Z01), (Join-Path $TmpDir $JAR_PART_ZIP), (Join-Path $TmpDir "combined.zip")) | ForEach-Object {
    if (Test-Path $_) { Remove-Item $_ -Force -ErrorAction SilentlyContinue; Write-Log "INFO" "已清理: $(Split-Path $_ -Leaf)" }
}

# ==========================================
# 启动服务
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  安装完成，启动服务"
Write-Log "INFO" "------------------------------------------"

Write-Log "INFO" "Matrix 本地服务已安装到: $LocalDir"
$startConfirm = Read-Host "是否立即启动服务? (Y/n)"
if ($startConfirm -ne "n") {
    $startScript = Join-Path $BinDir "start.ps1"
    if (Test-Path $startScript) { & $startScript } else { Write-Log "ERROR" "未找到 start.ps1" }
} else {
    Write-Log "INFO" "手动启动: matrix start"
}

Write-Log "INFO" "=========================================="
Write-Log "INFO" "  安装完成！"
Write-Log "INFO" "=========================================="
Write-Log "INFO" "WebUI: http://localhost:10908"
Write-Log "INFO" "API:   http://localhost:10906"
Write-Log "INFO" "命令: matrix start/stop/restart/status/logs/update/uninstall"
pause