<#
.SYNOPSIS
    Matrix 本地服务一键安装脚本 (Windows)
.DESCRIPTION
    自动检测系统环境、下载 Matrix 本地服务、JDK 21、WebUI 并完成安装
.NOTES
    对应 install.sh 的 PowerShell 实现
    版本: 1.0.3
    需要 PowerShell 5.1 或更高版本
    建议以管理员身份运行（非必须，但可以避免权限问题）
#>

# ==========================================
# 初始化
# ==========================================

$OutputEncoding = [Text.Encoding]::UTF8
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# 确保使用 TLS 1.2
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$JdksDir = Join-Path $MatrixHome "jdk21"
$CliDir = Join-Path (Join-Path $env:USERPROFILE ".local") "bin"

$BinDir = Join-Path $LocalDir "bin"
$DataDir = Join-Path $LocalDir "data"
$ConfigDir = Join-Path $LocalDir "config"
$SettingsDir = Join-Path $LocalDir "settings"
$LogsDir = Join-Path $LocalDir "logs"
$WebuiDir = Join-Path $LocalDir "webui"
$TmpDir = Join-Path $LocalDir "tmp"

$SubDirs = @($BinDir, $DataDir, $ConfigDir, $SettingsDir, $LogsDir, $WebuiDir, $TmpDir)

function Write-Log {
    param([string]$Level = "INFO", [string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message"
}

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

$VersionFile = Join-Path $ScriptDir "latest-version.txt"
if (-not (Test-Path $VersionFile)) {
    Write-Host "[INFO] 未找到本地版本文件，正在从 Gitee 下载..."
    $versionUrl = "https://gitee.com/za96421955/matrix/raw/latest/install/gitee/latest-version.txt"
    try {
        (New-Object System.Net.WebClient).DownloadFile($versionUrl, $VersionFile)
        Write-Host "[INFO] 版本信息文件下载完成"
    } catch {
        Exit-WithError "无法下载版本信息文件: $_"
    }
}

function Parse-VersionFile {
    param([string]$FilePath)
    $lines = Get-Content $FilePath -Encoding UTF8 -ErrorAction Stop
    $result = @{}
    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
        $eq = $trimmed.IndexOf("=")
        if ($eq -gt 0) {
            $key = $trimmed.Substring(0, $eq).Trim()
            $value = $trimmed.Substring($eq + 1).Trim()
            $result[$key] = $value
        }
    }
    return $result
}

function Resolve-VariableRefs {
    param([hashtable]$Config, [string]$Value)
    for ($i = 0; $i -lt 10; $i++) {
        $matches = [regex]::Matches($Value, '\$\{(\w+)\}')
        if ($matches.Count -eq 0) { break }
        foreach ($m in $matches) {
            $var = $m.Groups[1].Value
            if ($Config.ContainsKey($var)) { $Value = $Value.Replace($m.Groups[0].Value, $Config[$var]) }
        }
    }
    return $Value
}

$RawConfig = Parse-VersionFile $VersionFile
for ($i = 0; $i -lt 20; $i++) {
    $changed = $false
    foreach ($k in @($RawConfig.Keys)) {
        $nv = Resolve-VariableRefs $RawConfig $RawConfig[$k]
        if ($nv -ne $RawConfig[$k]) { $RawConfig[$k] = $nv; $changed = $true }
    }
    if (-not $changed) { break }
}

$ConfigTable = $RawConfig

$MATRIX_VERSION = $ConfigTable["MATRIX_VERSION"]
$RELEASE_TAG    = $ConfigTable["RELEASE_TAG"]
$RAW_BASE       = $ConfigTable["RAW_BASE"]
$RELEASE_BASE   = $ConfigTable["RELEASE_BASE"]
$JAR_FILE_NAME  = $ConfigTable["JAR_FILE_NAME"]
$JAR_PART_Z01   = $ConfigTable["JAR_PART_Z01"]
$JAR_PART_ZIP   = $ConfigTable["JAR_PART_ZIP"]
$WEBUI_ZIP_NAME = $ConfigTable["WEBUI_ZIP_NAME"]

$ARCH = $env:PROCESSOR_ARCHITECTURE
if ($ARCH -eq "AMD64") {
    $JDK_URL = $ConfigTable["JDK_URL_WIN_X86_64"]
    $JDK_FILENAME = $ConfigTable["JDK_FILENAME_WIN_X86_64"]
} elseif ($ARCH -eq "ARM64") {
    $JDK_URL = $ConfigTable["JDK_URL_WIN_ARM64"]
    $JDK_FILENAME = $ConfigTable["JDK_FILENAME_WIN_ARM64"]
} else {
    Exit-WithError "不支持的 CPU 架构: $ARCH"
}

# ==========================================
# 下载函数（使用 Invoke-WebRequest 显示进度条）
# ==========================================

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
            # Invoke-WebRequest 会自动显示传输进度条（百分比、速率等）
            Invoke-WebRequest -Uri $Url -OutFile $Destination -UserAgent "Matrix-Installer/1.0.3" -ErrorAction Stop
            if (Test-Path $Destination) {
                $size = (Get-Item $Destination).Length
                Write-Log "INFO" "下载完成: $Label ($size bytes)"
                return $true
            }
            Write-Log "WARN" "文件不存在，可能下载失败"
        } catch {
            Write-Log "WARN" "下载失败 (第 $i 次/$Retries): $_"
        }
        if ($i -lt $Retries) {
            $wait = $i * 3
            Write-Log "INFO" "等待 ${wait} 秒后重试..."
            Start-Sleep -Seconds $wait
        }
    }
    return $false
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
        Write-Log "INFO" "创建目录: $Path"
    }
}

function Download-And-Extract {
    param(
        [string]$Url,
        [string]$ZipPath,
        [string]$ExtractDir,
        [string]$Label
    )
    if (-not (Download-File -Url $Url -Destination $ZipPath -Label $Label)) { return $false }
    try {
        Write-Log "INFO" "正在解压 $Label ..."
        if (Test-Path $ExtractDir) { Remove-Item "$ExtractDir\*" -Recurse -Force -ErrorAction SilentlyContinue }
        else { New-Item -ItemType Directory -Path $ExtractDir -Force | Out-Null }
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
if ($osInfo) { Write-Log "INFO" "系统: $($osInfo.Caption) $($osInfo.Version)" }
else { Write-Log "INFO" "系统: Windows" }
Write-Log "INFO" "架构: $ARCH"
Write-Log "INFO" "用户: $env:USERNAME"
Write-Log "INFO" "安装目录: $LocalDir"
Write-Log "INFO" ""

$psVer = $PSVersionTable.PSVersion
if ($psVer.Major -lt 5) { Exit-WithError "需要 PowerShell 5.0+，当前: $($psVer.Major).$($psVer.Minor)" }
Write-Log "INFO" "PowerShell 版本: $($psVer.Major).$($psVer.Minor)"

# ==========================================
# 配置 API Key
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  配置 API Key"
Write-Log "INFO" "------------------------------------------"
$apiKey = Read-Host "请输入你的 DeepSeek API Key"
try {
    [Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", $apiKey, "User")
    Write-Log "INFO" "DEEPSEEK_API_KEY 已写入用户环境变量"
} catch {
    Write-Log "WARN" "写入环境变量失败，请手动设置: $_"
}

# ==========================================
# 创建目录结构
# ==========================================

Write-Log "INFO" "创建目录结构..."
Ensure-Directory $MatrixHome
foreach ($d in $SubDirs) { Ensure-Directory $d }

# ==========================================
# 下载核心 JAR
# ==========================================

Write-Log "INFO" "------------------------------------------"
Write-Log "INFO" "  下载核心文件"
Write-Log "INFO" "------------------------------------------"

$JarUrl = "$RELEASE_BASE/$RELEASE_TAG/$JAR_FILE_NAME"
$JarPath = Join-Path $LocalDir $JAR_FILE_NAME
$jarDownloaded = Download-File -Url $JarUrl -Destination $JarPath -Label "matrix-local JAR"

if (-not $jarDownloaded) {
    Write-Log "WARN" "完整 JAR 下载失败，尝试分卷下载..."
    $partZ01Url = "$RELEASE_BASE/$RELEASE_TAG/$JAR_PART_Z01"
    $partZipUrl = "$RELEASE_BASE/$RELEASE_TAG/$JAR_PART_ZIP"
    $z01Path = Join-Path $TmpDir $JAR_PART_Z01
    $zipPath = Join-Path $TmpDir $JAR_PART_ZIP

    $z01Ok = Download-File -Url $partZ01Url -Destination $z01Path -Label "JAR 分卷 1/2"
    $zipOk = Download-File -Url $partZipUrl -Destination $zipPath -Label "JAR 分卷 2/2"

    if ($z01Ok -and $zipOk) {
        Write-Log "INFO" "分卷下载完成，开始合并..."
        $mergedOk = $false

        # 7z
        try {
            $7z = @( "${env:ProgramFiles}\7-Zip\7z.exe", "${env:ProgramFiles(x86)}\7-Zip\7z.exe" ) | Where-Object { Test-Path $_ } | Select-Object -First 1
            if (-not $7z -and (Get-Command "7z" -ErrorAction SilentlyContinue)) { $7z = (Get-Command "7z").Source }
            if ($7z) {
                Write-Log "INFO" "使用 7z 解压..."
                & $7z x "$zipPath" -o"$TmpDir" -y | Out-Null
                $extracted = Join-Path $TmpDir $JAR_FILE_NAME
                if (Test-Path $extracted) { Copy-Item $extracted $JarPath -Force; $mergedOk = $true; Write-Log "INFO" "7z 成功" }
            }
        } catch { Write-Log "WARN" "7z 失败: $_" }

        # copy /B
        if (-not $mergedOk) {
            try {
                $combined = Join-Path $TmpDir "combined.zip"
                cmd /c "copy /B `"$z01Path`" + `"$zipPath`" `"$combined`" >nul 2>&1"
                if (Test-Path $combined) {
                    Expand-Archive $combined -DestinationPath $TmpDir -Force
                    $extracted = Join-Path $TmpDir $JAR_FILE_NAME
                    if (Test-Path $extracted) { Copy-Item $extracted $JarPath -Force; $mergedOk = $true; Write-Log "INFO" "copy /B 成功" }
                }
            } catch { Write-Log "WARN" "copy /B 失败: $_" }
        }

        # 二进制合并
        if (-not $mergedOk) {
            try {
                $combined = Join-Path $TmpDir "combined.zip"
                $b1 = [IO.File]::ReadAllBytes($z01Path)
                $b2 = [IO.File]::ReadAllBytes($zipPath)
                $all = New-Object byte[] ($b1.Length + $b2.Length)
                [Buffer]::BlockCopy($b1, 0, $all, 0, $b1.Length)
                [Buffer]::BlockCopy($b2, 0, $all, $b1.Length, $b2.Length)
                [IO.File]::WriteAllBytes($combined, $all)
                Expand-Archive $combined -DestinationPath $TmpDir -Force
                $extracted = Join-Path $TmpDir $JAR_FILE_NAME
                if (Test-Path $extracted) { Copy-Item $extracted $JarPath -Force; $mergedOk = $true; Write-Log "INFO" "二进制合并成功" }
            } catch { Write-Log "WARN" "二进制合并失败: $_" }
        }

        if (-not $mergedOk) { Exit-WithError "JAR 合并失败，请手动下载并放置到 $JarPath" }
    } else { Exit-WithError "分卷下载失败" }
}

# ==========================================
# 下载 bin/ 脚本
# ==========================================

Write-Log "INFO" "下载 bin/ 脚本..."
@("proxy_server.py","start.ps1","stop.ps1","restart.ps1") | ForEach-Object {
    $dest = Join-Path $BinDir $_
    Download-File -Url "$RAW_BASE/$MATRIX_VERSION/install/bin/$_" -Destination $dest -Label $_ -ErrorAction SilentlyContinue
}

# ==========================================
# 下载 config/ 配置
# ==========================================

Write-Log "INFO" "下载 config/ ..."
@("application.yml","banner.txt") | ForEach-Object {
    $dest = Join-Path $ConfigDir $_
    if (Test-Path $dest) { Write-Log "INFO" "跳过 $_ (已存在)"; return }
    Download-File -Url "$RAW_BASE/$MATRIX_VERSION/install/config/$_" -Destination $dest -Label "config/$_"
}

$appYml = Join-Path $ConfigDir "application.yml"
if (Test-Path $appYml) {
    $content = Get-Content $appYml -Raw
    $content = $content -replace 'base-path:.*', "base-path: $LocalDir"
    [IO.File]::WriteAllText($appYml, $content, [Text.UTF8Encoding]::new($false))
    Write-Log "INFO" "base-path 已更新为 $LocalDir"
}
[IO.File]::WriteAllText((Join-Path $ConfigDir "server.url"), "https://gitee.com/za96421955/matrix/raw/latest/install/gitee/latest-version.txt", [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $ConfigDir "version"), $MATRIX_VERSION, [Text.UTF8Encoding]::new($false))

# ==========================================
# 下载 data/
# ==========================================

$dataDest = Join-Path $DataDir "schema.sql"
if (Test-Path $dataDest) { Write-Log "INFO" "跳过 schema.sql (已存在)" }
else { Download-File -Url "$RAW_BASE/$MATRIX_VERSION/install/data/schema.sql" -Destination $dataDest -Label "schema.sql" }

# ==========================================
# 下载 settings/
# ==========================================

@("MEMORY.md","risk-level.yml") | ForEach-Object {
    $dest = Join-Path $SettingsDir $_
    if (Test-Path $dest) { Write-Log "INFO" "跳过 $_ (已存在)"; return }
    Download-File -Url "$RAW_BASE/$MATRIX_VERSION/install/settings/$_" -Destination $dest -Label "settings/$_"
}

$skillDir = Join-Path $SettingsDir "skill"
Ensure-Directory $skillDir
@("query-typhoon") | ForEach-Object {
    $d = Join-Path $skillDir $_
    if (Test-Path $d) { Write-Log "INFO" "跳过 skill/$_ (已存在)"; return }
    Ensure-Directory $d
    Download-File -Url "$RAW_BASE/$MATRIX_VERSION/install/settings/skill/$_/SKILL.md" -Destination (Join-Path $d "SKILL.md") -Label "settings/skill/$_/SKILL.md"
}

# ==========================================
# 下载 WebUI
# ==========================================

Write-Log "INFO" "下载 WebUI..."
$WebuiZipUrl = "$RELEASE_BASE/$RELEASE_TAG/$WEBUI_ZIP_NAME"
$WebuiZipPath = Join-Path $TmpDir $WEBUI_ZIP_NAME
if (Download-And-Extract -Url $WebuiZipUrl -ZipPath $WebuiZipPath -ExtractDir $WebuiDir -Label "WebUI") {
    $distDir = Join-Path $WebuiDir "dist"
    if (Test-Path (Join-Path $distDir "index.html")) {
        Write-Log "INFO" "移动 dist/ 内容..."
        Get-ChildItem $distDir -Force | Move-Item -Destination $WebuiDir -Force
        Remove-Item $distDir -Recurse -Force
    }
    Remove-Item $WebuiZipPath -Force -ErrorAction SilentlyContinue
    Write-Log "INFO" "WebUI 安装完成"
} else { Exit-WithError "WebUI 下载/解压失败" }

# ==========================================
# 下载 JDK 21
# ==========================================

Write-Log "INFO" "下载 JDK 21..."
$JdkZipPath = Join-Path $TmpDir $JDK_FILENAME
if (Test-Path (Join-Path $JdksDir "bin\java.exe")) {
    Write-Log "INFO" "JDK 21 已安装，跳过"
} else {
    if (Download-File -Url $JDK_URL -Destination $JdkZipPath -Label "JDK 21") {
        Write-Log "INFO" "解压 JDK..."
        Ensure-Directory $JdksDir
        Expand-Archive $JdkZipPath -DestinationPath $JdksDir -Force
        $nested = Get-ChildItem $JdksDir -Directory | Where-Object { $_.Name -like "jdk-21*" }
        if ($nested) {
            Get-ChildItem $nested.FullName | Move-Item -Destination $JdksDir -Force
            Remove-Item $nested.FullName -Recurse -Force
        }
        Write-Log "INFO" "JDK 21 安装完成"
    } else { Exit-WithError "JDK 21 下载失败" }
}

# ==========================================
# 安装 matrix CLI
# ==========================================

Write-Log "INFO" "安装 matrix CLI..."
Ensure-Directory $CliDir

$matrixPs1 = Join-Path $CliDir "matrix.ps1"
@'
<#
.SYNOPSIS
    Matrix 本地服务命令行工具
#>

param(
    [Parameter(Position=0)]
    [ValidateSet("start","stop","restart","status","logs","webui-logs","update","uninstall","help")]
    [string]$Command = "help"
)

$MatrixHome = Join-Path $env:USERPROFILE ".matrix"
$LocalDir = Join-Path $MatrixHome "local"
$BinDir = Join-Path $LocalDir "bin"
$LogsDir = Join-Path $LocalDir "logs"
$ConfigDir = Join-Path $LocalDir "config"
$TmpDir = Join-Path $LocalDir "tmp"

function Write-Log($Level="INFO", $Message) {
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [$Level] $Message"
}

function Get-LatestVersionInfo {
    # 使用与安装脚本一致的 URL
    $url = "https://gitee.com/za96421955/matrix/raw/latest/install/gitee/latest-version.txt"
    try {
        Write-Log "INFO" "正在获取版本信息..."
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("User-Agent", "Matrix-CLI/1.0.3")
        # 使用 DownloadData + UTF-8 解码，避免 WebClient.DownloadString 的默认编码问题
        # （中文 UTF-8 多字节序列在默认编码下会被误解析，导致换行符丢失）
        $rawBytes = $wc.DownloadData($url)
        $content = [System.Text.Encoding]::UTF8.GetString($rawBytes)
    } catch {
        Write-Log "ERROR" "无法下载版本信息文件: $_"
        return $null
    }

    $res = @{}
    $reader = [System.IO.StringReader]::new($content)
    while (($line = $reader.ReadLine()) -ne $null) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
        $eq = $trimmed.IndexOf("=")
        if ($eq -gt 0) {
            $key = $trimmed.Substring(0, $eq).Trim()
            $value = $trimmed.Substring($eq + 1).Trim()
            $res[$key] = $value
        }
    }
    $reader.Dispose()

    # 多轮变量引用替换
    for ($i = 0; $i -lt 10; $i++) {
        $changed = $false
        foreach ($k in @($res.Keys)) {
            $matches = [regex]::Matches($res[$k], '\$\{(\w+)\}')
            if ($matches.Count -eq 0) { continue }
            foreach ($m in $matches) {
                $var = $m.Groups[1].Value
                if ($res.ContainsKey($var)) {
                    $res[$k] = $res[$k].Replace($m.Groups[0].Value, $res[$var])
                    $changed = $true
                }
            }
        }
        if (-not $changed) { break }
    }

    # 必须包含 MATRIX_VERSION
    if (-not $res.ContainsKey("MATRIX_VERSION") -or [string]::IsNullOrEmpty($res["MATRIX_VERSION"])) {
        Write-Log "ERROR" "版本信息中缺少 MATRIX_VERSION，请检查网络或文件内容"
        return $null
    }

    return $res
}

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
            Invoke-WebRequest -Uri $Url -OutFile $Destination -UserAgent "Matrix-Installer/1.0.3" -ErrorAction Stop
            if (Test-Path $Destination) {
                $size = (Get-Item $Destination).Length
                Write-Log "INFO" "下载完成: $Label ($size bytes)"
                return $true
            }
            Write-Log "WARN" "文件不存在，可能下载失败"
        } catch {
            Write-Log "WARN" "下载失败 (第 $i 次/$Retries): $_"
        }
        if ($i -lt $Retries) {
            $wait = $i * 3
            Write-Log "INFO" "等待 ${wait} 秒后重试..."
            Start-Sleep -Seconds $wait
        }
    }
    return $false
}

switch ($Command) {
    "start" {
        $s = Join-Path $BinDir "start.ps1"
        if (Test-Path $s) { & $s } else { Write-Log "ERROR" "未找到 start.ps1" }
    }
    "stop" {
        $s = Join-Path $BinDir "stop.ps1"
        if (Test-Path $s) { & $s } else { Write-Log "ERROR" "未找到 stop.ps1" }
    }
    "restart" {
        $s = Join-Path $BinDir "restart.ps1"
        if (Test-Path $s) { & $s } else { Write-Log "ERROR" "未找到 restart.ps1" }
    }
    "status" {
        Write-Log "INFO" "Matrix 本地服务状态:"
        $svcPid = Join-Path $BinDir "app.pid"
        if (Test-Path $svcPid) {
            $p = (Get-Content $svcPid -Raw).Trim()
            if (Get-Process -Id $p -ErrorAction SilentlyContinue) { Write-Log "INFO" "  后端: 运行中 (PID=$p)" }
            else { Write-Log "INFO" "  后端: 未运行" }
        } else { Write-Log "INFO" "  后端: 未运行" }
        $wPid = Join-Path $BinDir "webui.pid"
        if (Test-Path $wPid) {
            $p = (Get-Content $wPid -Raw).Trim()
            if (Get-Process -Id $p -ErrorAction SilentlyContinue) { Write-Log "INFO" "  WebUI: 运行中 (PID=$p)" }
            else { Write-Log "INFO" "  WebUI: 未运行" }
        } else { Write-Log "INFO" "  WebUI: 未运行" }
        Write-Log "INFO" "  端口检查需要管理员权限"
    }
    "logs" {
        $f = Join-Path $LogsDir "app.log"
        if (Test-Path $f) { Get-Content $f -Tail 50 -Wait } else { Write-Log "WARN" "日志文件不存在" }
    }
    "webui-logs" {
        $f = Join-Path $LogsDir "webui.log"
        if (Test-Path $f) { Get-Content $f -Tail 50 -Wait } else { Write-Log "WARN" "日志文件不存在" }
    }
    "update" {
        Write-Log "INFO" "检查更新..."
        $info = Get-LatestVersionInfo
        if (-not $info) {
            Write-Log "ERROR" "获取版本信息失败，更新中止"
            exit 1
        }
        $rv = $info["MATRIX_VERSION"]
        Write-Log "INFO" "当前最新版本: v$rv"
        $stopScript = Join-Path $BinDir "stop.ps1"
        if (Test-Path $stopScript) { & $stopScript }
        Start-Sleep 2
        $releaseUrl = "$($info['RELEASE_BASE'])/$($info['RELEASE_TAG'])"
        $jarPath = Join-Path $LocalDir $info['JAR_FILE_NAME']
        $jarDownloaded = Download-File -Url "$releaseUrl/$($info['JAR_FILE_NAME'])" -Destination $jarPath -Label "matrix-local JAR"
        if (-not $jarDownloaded) {
            Write-Log "WARN" "完整 JAR 下载失败，尝试分卷下载..."
            $partZ01Url = "$releaseUrl/$($info['JAR_PART_Z01'])"
            $partZipUrl = "$releaseUrl/$($info['JAR_PART_ZIP'])"
            $z01Path = Join-Path $TmpDir $info['JAR_PART_Z01']
            $zipPath = Join-Path $TmpDir $info['JAR_PART_ZIP']
            $z01Ok = Download-File -Url $partZ01Url -Destination $z01Path -Label "JAR 分卷 1/2"
            $zipOk = Download-File -Url $partZipUrl -Destination $zipPath -Label "JAR 分卷 2/2"
            if ($z01Ok -and $zipOk) {
                Write-Log "INFO" "分卷下载完成，开始合并..."
                $mergedOk = $false
                # 7z
                try {
                    $7z = @( "${env:ProgramFiles}\7-Zip\7z.exe", "${env:ProgramFiles(x86)}\7-Zip\7z.exe" ) | Where-Object { Test-Path $_ } | Select-Object -First 1
                    if (-not $7z -and (Get-Command "7z" -ErrorAction SilentlyContinue)) { $7z = (Get-Command "7z").Source }
                    if ($7z) {
                        Write-Log "INFO" "使用 7z 解压..."
                        & $7z x "$zipPath" -o"$TmpDir" -y | Out-Null
                        $extracted = Join-Path $TmpDir $info['JAR_FILE_NAME']
                        if (Test-Path $extracted) { Copy-Item $extracted $jarPath -Force; $mergedOk = $true; Write-Log "INFO" "7z 成功" }
                    }
                } catch { Write-Log "WARN" "7z 失败: $_" }
                # copy /B
                if (-not $mergedOk) {
                    try {
                        $combined = Join-Path $TmpDir "combined.zip"
                        cmd /c "copy /B `"$z01Path`" + `"$zipPath`" `"$combined`" >nul 2>&1"
                        if (Test-Path $combined) {
                            Expand-Archive $combined -DestinationPath $TmpDir -Force
                            $extracted = Join-Path $TmpDir $info['JAR_FILE_NAME']
                            if (Test-Path $extracted) { Copy-Item $extracted $jarPath -Force; $mergedOk = $true; Write-Log "INFO" "copy /B 成功" }
                        }
                    } catch { Write-Log "WARN" "copy /B 失败: $_" }
                }
                # 二进制合并
                if (-not $mergedOk) {
                    try {
                        $combined = Join-Path $TmpDir "combined.zip"
                        $b1 = [IO.File]::ReadAllBytes($z01Path)
                        $b2 = [IO.File]::ReadAllBytes($zipPath)
                        $all = New-Object byte[] ($b1.Length + $b2.Length)
                        [Buffer]::BlockCopy($b1, 0, $all, 0, $b1.Length)
                        [Buffer]::BlockCopy($b2, 0, $all, $b1.Length, $b2.Length)
                        [IO.File]::WriteAllBytes($combined, $all)
                        Expand-Archive $combined -DestinationPath $TmpDir -Force
                        $extracted = Join-Path $TmpDir $info['JAR_FILE_NAME']
                        if (Test-Path $extracted) { Copy-Item $extracted $jarPath -Force; $mergedOk = $true; Write-Log "INFO" "二进制合并成功" }
                    } catch { Write-Log "WARN" "二进制合并失败: $_" }
                }
                if (-not $mergedOk) { Write-Log "ERROR" "JAR 合并失败，请手动下载并放置到 $jarPath"; exit 1 }
            } else { Write-Log "ERROR" "分卷下载失败，更新中止"; exit 1 }
        }
        Write-Log "INFO" "更新完成，重启服务"
        $stopScript = Join-Path $BinDir "restart.ps1"
        if (Test-Path $stopScript) { & $stopScript }
    }
    "uninstall" {
        Write-Log "WARN" "确认卸载? 输入 y"
        if ((Read-Host) -eq 'y') {
            $stopScript = Join-Path $BinDir "stop.ps1"
            if (Test-Path $stopScript) { & $stopScript }
            Remove-Item $LocalDir -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item (Join-Path "$env:USERPROFILE\.local\bin" "matrix.ps1") -Force -ErrorAction SilentlyContinue
            Remove-Item (Join-Path "$env:USERPROFILE\.local\bin" "matrix.bat") -Force -ErrorAction SilentlyContinue
            Write-Log "INFO" "卸载完成"
        }
    }
    "help" {
        Write-Host "`nMatrix CLI - 命令: start, stop, restart, status, logs, webui-logs, update, uninstall, help`n"
    }
}
'@ | Out-File -FilePath $matrixPs1 -Encoding UTF8 -Force

$matrixBat = Join-Path $CliDir "matrix.bat"
@"
@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0matrix.ps1" %*
"@ | Out-File -FilePath $matrixBat -Encoding ASCII -Force

Write-Log "INFO" "CLI 安装完成: $matrixPs1, $matrixBat"

# ==========================================
# 配置 PATH
# ==========================================

Write-Log "INFO" "配置 PATH..."
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$CliDir*") {
    try {
        [Environment]::SetEnvironmentVariable("Path", "$CliDir;$userPath", "User")
        $env:Path = "$CliDir;$env:Path"
        Write-Log "INFO" "已将 $CliDir 添加到用户 PATH"
    } catch { Write-Log "WARN" "PATH 设置失败，请手动添加: $CliDir" }
} else { Write-Log "INFO" "PATH 已包含 $CliDir" }

# ==========================================
# 清理临时文件
# ==========================================

Write-Log "INFO" "清理临时文件..."
@($JdkZipPath, $WebuiZipPath, (Join-Path $TmpDir $JAR_PART_Z01), (Join-Path $TmpDir $JAR_PART_ZIP)) | ForEach-Object {
    if (Test-Path $_) { Remove-Item $_ -Force -ErrorAction SilentlyContinue }
}

Write-Log "INFO" "=========================================="
Write-Log "INFO" "Matrix 安装完成，执行以下指令，启动服务："
Write-Log "INFO" "matrix start"
Write-Log "INFO" "=========================================="
$stopScript = Join-Path $BinDir "restart.ps1"
if (Test-Path $stopScript) { & $stopScript }
