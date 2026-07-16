---
name: query-typhoon
description: 查询全球热带气旋（台风/飓风/气旋）最新预警报文，数据源为 NOAA/JTWC/JMA 等官方机构原始报文
auth: cc
version: '3.0'
enabled: true
---

# query-typhoon

查询全球热带气旋（台风/飓风/气旋）最新预警报文，数据源为 NOAA TG FTP 服务器的官方机构原始报文。

## 能力说明

本 Skill 通过 `cli-executor` 工具执行 curl/PowerShell 命令，从 NOAA 服务器获取热带气旋预警报文。支持按洋区、发报机构查询，可自动发现最新警告文件或指定警告编号，也可按台风名称关键词过滤结果。

技术特性：
- 数据源为 NOAA 服务器原始报文，实时准确
- 自动发现最新警告文件（服务器原生按修改时间倒序排列，无需本地日期解析）
- 支持全球各大洋区和主要发报机构
- 支持按台风名称关键词过滤结果
- 纯命令行执行，无需额外服务
- **同时支持 macOS/Linux (curl+grep) 和 Windows (PowerShell)**

## 触发场景

当用户表达以下任一意图时触发：
- 查询台风/飓风/气旋信息
- 查看最新台风/飓风/气旋预警
- 查询某某台风（如巴威、BAVI、摩羯、YAGI 等）的最新情况
- 某个台风/飓风/气旋现在到哪里了
- 查看台风/飓风/气旋路径预报
- 查询热带气旋警告
- 查询西北太平洋/印度洋/北大西洋等洋区的台风情况

## 参数说明

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| basin | string | 否 | 洋区代码。默认值：wp（西北太平洋） |
| agency | string | 否 | 发报机构代码。默认根据洋区自动选择 |
| warningNumber | integer | 否 | 指定警告编号（如 31）。不指定时自动发现最新 |
| stormName | string | 否 | 台风名称关键词过滤（如 BAVI、巴威、09W、摩羯、YAGI 等） |

## 技术实现

### 核心发现：NOAA 服务器支持按修改时间排序

NOAA 服务器的 HTML 目录列表支持 URL 参数：
- `?C=M;O=D` = 按修改时间倒序（newest first）
- `?C=N;O=D` = 按文件名倒序

使用 `?C=M;O=D` 获取文件列表时，最新修改的文件排在最前面。这完全取代了之前版本中复杂的 awk/sed 日期解析和排序逻辑，将自动发现简化为一行 grep 命令。

### 支持的洋区代码（basin）与默认发报机构

| 代码 | 洋区 | 默认发报机构 |
|---|---|---|
| wp | 西北太平洋（默认） | pgtw (JTWC 关岛) |
| io | 印度洋 | fmee (毛里求斯气象局) |
| at | 北大西洋 | knhc (NHC 美国国家飓风中心) |
| ep | 东北太平洋 | knhc (NHC 美国国家飓风中心) |
| cp | 中太平洋 | kwnh (CPHC 中太平洋飓风中心) |
| sh | 南半球 | amsl (南半球) |
| jp | 日本（JMA） | rjtd (JMA 日本气象厅) |
| ko | 韩国（KMA） | rksl (KMA 韩国气象厅) |

### 发报机构代码与 NOAA 区域代码映射

| 机构代码 | 机构名称 | NOAA 区域代码 | 说明 |
|---|---|---|---|
| pgtw | JTWC 关岛 | pn | 联合台风警报中心，覆盖西北太平洋 |
| rjtd | JMA 日本气象厅 | jp | 日本区域专业气象中心 |
| rksl | KMA 韩国气象厅 | ko | 韩国气象厅 |
| knhc | NHC 美国国家飓风中心 | nt (北大西洋) / pz (东北太平洋) | 根据洋区区分 |
| kwnh | CPHC 中太平洋飓风中心 | pa | 覆盖中太平洋 |
| fmee | 毛里求斯气象局 | io | 覆盖西南印度洋 |
| amsl | 南半球 | sh | 覆盖南半球 |

### 区域代码解析规则

当 agency 为 knhc (NHC) 时：
- 若 basin 为 ep（东北太平洋），区域代码为 pz
- 否则（北大西洋等），区域代码为 nt

其他机构直接使用映射表中的区域代码。

### 文件命名规则与产品类型

NOAA 原始报文文件命名格式：`wt{regionCode}{number}.{agency}..txt`

其中 number 通常为两位数：
- **pgtw（JTWC）产品编号规律**：
  - `wtpn3X` = 热带气旋警告公报（Tropical Cyclone Warning）—— **最常用**
  - `wtpn5X` = 预报推理解析（Prognostic Reasoning）
  - `wtpn2X` = 其他产品

文件存放目录：`https://tgftp.nws.noaa.gov/data/raw/wt/`

### 直接获取指定警告编号

```bash
# 格式：curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/wt{regionCode}{num}.{agency}..txt"
# 示例：获取西北太平洋第31号 JTWC 警告
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/wtpn31.pgtw..txt"
```

其中 num 为两位数的警告编号（如 1 -> "01", 31 -> "31"）。

### 自动发现最新警告文件（核心优化！）

关键方法：使用 `?C=M;O=D` 参数获取按修改时间倒序排列的目录列表，然后用 grep 提取第一个匹配的文件名。

#### Unix/macOS（使用 curl + grep）

```bash
# 通用模板
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wt{region}[0-9]+\.{agency}\.\.txt' | head -1)
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST"

# 示例：获取西北太平洋最新的 JTWC 警告公报
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wtpn3[0-9]\.pgtw\.\.txt' | head -1)
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST"

# 如果要获取预报推理解析（prognostic reasoning），使用：
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wtpn5[0-9]\.pgtw\.\.txt' | head -1)
```

#### Windows PowerShell（使用 curl + Select-String）

```powershell
# 获取最新的 JTWC 警告公报
$html = curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D"
$latestFile = [regex]::Match($html, 'wtpn3[0-9]\.pgtw\.\.txt').Value
Write-Output "Latest: $latestFile"
curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$latestFile"

# 或使用 Select-String
$latestFile = (curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | Select-String -Pattern 'wtpn3[0-9]\.pgtw\.\.txt' | Select-Object -First 1).Matches.Value
curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$latestFile"
```

### 按台风名称过滤

获取到完整报文后，用 grep / Select-String 按名称过滤：

#### Unix/macOS
```bash
# 在结果中过滤包含台风名称的行
echo "$报文内容" | grep -i "BAVI"
```

#### Windows PowerShell
```powershell
# 在结果中过滤包含台风名称的行
$content | Select-String -Pattern "BAVI"
```

## 执行步骤

使用 `cli-executor` 工具执行。根据操作系统选择对应命令。

### 步骤1：确定参数

根据用户的问题确定参数：
- basin：如未指定，默认为 wp（西北太平洋）
- agency：如未指定，根据 basin 选择默认发报机构
- warningNumber：如未指定，自动发现最新
- stormName：如用户提到具体台风名称，设置此参数

### 步骤2：确定 NOAA 区域代码

根据 basin 和 agency 查表确定区域代码。

### 步骤3：构建并执行查询命令

#### 场景A：指定了 warningNumber（直接获取）

**Unix/macOS：**
```bash
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/wt{region}{num}.{agency}..txt"
```

**Windows PowerShell：**
```powershell
curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/wt{region}{num}.{agency}..txt"
```

#### 场景B：未指定 warningNumber，自动发现最新

**Unix/macOS：**
```bash
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wt{region}[0-9]+\.{agency}\.\.txt' | head -1)
echo "=== Latest file: $LATEST ==="
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST"
```

**Windows PowerShell：**
```powershell
$html = curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D"
$latestFile = [regex]::Match($html, 'wt{region}[0-9]+\.{agency}\.\.txt').Value
Write-Output "=== Latest file: $latestFile ==="
curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$latestFile"
```

#### 场景C：按名称过滤（可选）

如果用户指定了 stormName，在获取到的报文中搜索包含该名称的行。同时显示完整报文。

### 步骤4：返回结果

将获取到的原始报文返回给用户，并对关键信息做简要解读：
- 台风名称和编号
- 当前位置（经纬度）
- 最大持续风速和中心气压
- 移动方向和速度
- 路径预报

## 使用示例

### 示例1：查询西北太平洋最新台风预警（默认）

用户说"查一下最新的台风"。
- basin=wp, agency=pgtw（默认）
- 不指定 warningNumber，自动发现最新

**Unix/macOS 命令：**
```bash
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wtpn3[0-9]\.pgtw\.\.txt' | head -1)
echo "=== Latest JTWC Warning: $LATEST ==="
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST"
```

**Windows PowerShell 命令：**
```powershell
$html = curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D"
$f = [regex]::Match($html, 'wtpn3[0-9]\.pgtw\.\.txt').Value
Write-Output "=== Latest JTWC Warning: $f ==="
curl.exe -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$f"
```

### 示例2：查询印度洋最新气旋预警

用户说"印度洋最近有气旋吗"。
- basin=io, agency=fmee, region=io

```bash
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wtio[0-9]+\.fmee\.\.txt' | head -1)
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST"
```

### 示例3：查询北大西洋最新飓风预警

用户说"北大西洋有飓风吗"。
- basin=at, agency=knhc, region=nt

```bash
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wtnt[0-9]+\.knhc\.\.txt' | head -1)
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST"
```

### 示例4：查询特定台风的最新情况

用户说"查一下台风摩羯的最新情况"。
- basin=wp, agency=pgtw, stormName=YAGI
- 先获取最新报文，然后在结果中过滤

```bash
# 获取最新 JTWC 警告并过滤
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wtpn3[0-9]\.pgtw\.\.txt' | head -1)
CONTENT=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST")
echo "=== Full warning ==="
echo "$CONTENT"
echo "=== Filtered by YAGI ==="
echo "$CONTENT" | grep -i "YAGI"
```

### 示例5：指定警告编号查询

用户说"看下西北太平洋第31号警告"。
- basin=wp, agency=pgtw, warningNumber=31

```bash
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/wtpn31.pgtw..txt"
```

### 示例6：获取预报推理解析（Prognostic Reasoning）

用户说"看看台风巴威的预报分析"。
- 使用 wtpn5X 模式获取预报推理解析

```bash
LATEST=$(curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/?C=M;O=D" | grep -oE 'wtpn5[0-9]\.pgtw\.\.txt' | head -1)
curl -sL "https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST"
```

## 注意事项

1. 数据源为 NOAA 官方原始报文，格式为英文，包含台风位置、强度、移动方向、风速等专业气象信息
2. 自动发现最新警告时，使用 `?C=M;O=D` 参数让服务器按修改时间倒序排列，取第一个匹配文件即可。无需在本地解析日期排序。
3. JTWC 产品类型：`wtpn3X` = 警告公报（推荐），`wtpn5X` = 预报推理解析。默认使用 `wtpn3X`。
4. 如果指定了 stormName 但未匹配到，返回完整报文并提示用户未匹配
5. 警告编号为 NOAA 服务器上的文件编号，不同机构编号规则不同
6. 如需查询历史台风数据，请指定具体的 warningNumber
7. 执行 curl 命令时使用 `cli-executor` 工具，workingDirectory 设置为临时目录（如 /tmp）
8. **Windows 环境**：使用 `curl.exe`（Windows 10+ 自带）而非 `curl`，使用 PowerShell 的 `Select-String` 替代 `grep`，使用 `[regex]::Match()` 或 `Select-Object -First 1` 替代 `head -1`
9. **macOS 环境**：使用 `curl` 和 `grep`，grep 需使用 `-oE` 选项（`-o`=仅输出匹配部分，`-E`=扩展正则表达式）
10. `cli-executor` 工具的 workingDirectory 参数设为 `/tmp` 或系统临时目录即可
