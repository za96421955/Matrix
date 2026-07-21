#!/bin/bash
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# ============================================================
# matrix 一键安装脚本
# ============================================================

# ---------- 日志函数 ----------
log_info() { echo "[$(date '+%H:%M:%S')] [INFO] $1"; }
log_warn() { echo "[$(date '+%H:%M:%S')] [WARN] $1"; }
log_error() { echo "[$(date '+%H:%M:%S')] [ERROR] $1"; }

# ---------- 智能获取 profile 文件列表 ----------
get_profile_files() {
    local files=()
    local default_shell
    default_shell=$(basename "$SHELL" 2>/dev/null)

    case "$default_shell" in
        zsh)  files+=("$HOME/.zshrc") ;;
        bash) files+=("$HOME/.bash_profile") ;;
        *)    files+=("$HOME/.profile") ;;
    esac

    if [ -n "$BASH_VERSION" ] && [ "$default_shell" != "bash" ]; then
        files+=("$HOME/.bash_profile" "$HOME/.bashrc")
    fi

    printf '%s\n' "${files[@]}" | sort -u | tr '\n' ' '
}

# ---------- 写入环境变量到所有需要的 profile ----------
write_to_profiles() {
    local entry="$1"
    local files
    read -ra files <<< "$(get_profile_files)"

    for profile in "${files[@]}"; do
        if [ ! -f "$profile" ]; then
            mkdir -p "$(dirname "$profile")"
            touch "$profile"
            echo "# Created by matrix installer" >> "$profile"
            log_info "✓ 创建 $profile"
        fi
        if ! grep -qxF "$entry" "$profile"; then
            echo "$entry" >> "$profile"
            log_info "✓ 已写入 $profile"
        else
            log_info "✓ $profile 已包含该配置，跳过"
        fi
    done
}

# ---------- 参数解析 ----------
SERVER="https://raw.githubusercontent.com/XXXXXX/matrix/master/install"

# ---------- 系统架构检测 ----------
OS=$(uname -s)
ARCH=$(uname -m)

case "$OS" in
    Linux)          os_type="linux" ;;
    Darwin)         os_type="darwin" ;;
    MINGW*|MSYS*|CYGWIN*) os_type="windows" ;;
    *) log_error "不支持的操作系统: $OS"; exit 1 ;;
esac

case "$ARCH" in
    x86_64|amd64) arch_type="x64" ;;
    aarch64|arm64) arch_type="aarch64" ;;
    *) log_error "不支持的架构: $ARCH"; exit 1 ;;
esac

log_info "系统: ${OS} ${ARCH}"

# ---------- DEEPSEEK_API_KEY ----------
log_info "检查 deepseek api key ..."
if [ -n "$DEEPSEEK_API_KEY" ]; then
    log_info "✓ 已设置"
else
    log_warn "未检测到 DEEPSEEK_API_KEY"
    echo "请输入你的 deepseek api key (输入后回车): "
    read -r -s DEEPSEEK_API_KEY
    [ -z "$DEEPSEEK_API_KEY" ] && { log_error "key 不能为空"; exit 1; }

    printf -v escaped_key '%q' "$DEEPSEEK_API_KEY"
    write_to_profiles "export DEEPSEEK_API_KEY=$escaped_key"
    export DEEPSEEK_API_KEY
    log_info "✓ 当前会话已生效"
fi

# ---------- 创建目录 ----------
INSTALL_DIR="$HOME/.matrix/client"
CLI_DIR="$HOME/.local/bin"

log_info "创建目录..."
mkdir -p "$INSTALL_DIR"/{bin,data,config,settings,logs} "$CLI_DIR"
log_info "✓ 目录就绪"

# ---------- 保存服务器地址 (供 matrix update 使用) ----------
echo "$SERVER" > "$INSTALL_DIR/config/server.url"
log_info "✓ 服务器地址已保存"

# ---------- 下载 JAR ----------
JAR_URL="$SERVER/matrix-client.jar"
JAR_FILE="$INSTALL_DIR/matrix-client.jar"
log_info "下载 JAR ..."
{ curl -# -fL "$JAR_URL" -o "$JAR_FILE" && log_info "✓ JAR 完成"; } || { log_error "下载失败: $JAR_URL"; exit 1; }

# ---------- 下载 bin/ 脚本 ----------
BIN_FILES="start.sh stop.sh restart.sh"
log_info "下载 bin/ 脚本 ..."
for f in $BIN_FILES; do
    curl -# -fL "$SERVER/bin/$f" -o "$INSTALL_DIR/bin/$f" || { log_error "下载 $f 失败"; exit 1; }
done
chmod +x "$INSTALL_DIR/bin/"*.sh
log_info "✓ bin/ 完成"

# ---------- 下载 config/ (跳过已存在) ----------
CONFIG_FILES="application.yml banner.txt"
log_info "下载 config/ ..."
for f in $CONFIG_FILES; do
    fp="$INSTALL_DIR/config/$f"
    if [ -f "$fp" ]; then
        log_info "✓ $f 已存在，跳过"
    else
        curl -# -fL "$SERVER/config/$f" -o "$fp" || { log_error "下载 $f 失败"; exit 1; }
        log_info "✓ $f 下载完成"
    fi
done
# 更新 base-path
APPLICATION_YML="$INSTALL_DIR/config/application.yml"
if [ -f "$APPLICATION_YML" ]; then
    if [ "$os_type" = "darwin" ]; then
        sed -i '' "s|base-path:.*|base-path: $INSTALL_DIR|" "$APPLICATION_YML"
    else
        sed -i "s|base-path:.*|base-path: $INSTALL_DIR|" "$APPLICATION_YML"
    fi
    log_info "✓ base-path 已更新"
fi

# ---------- 下载 settings/ ----------
SETTINGS_FILES=(
    "settings/models.yml"
    "settings/risk-level.yml"
    "settings/skill/mac_scan_pdf/README.md"
    "settings/skill/mac_scan_pdf/SKILL.md"
    "settings/skill/mac_scan_pdf/bin/mac-scan-pdf.sh"
    "settings/skill/mac_scan_pdf/bin/ocr.swift"
    "settings/skill/mac_scan_pdf/bin/pdftopng.swift"
)
log_info "下载 settings/ ..."
for rp in "${SETTINGS_FILES[@]}"; do
    fp="$INSTALL_DIR/$rp"
    if [ -f "$fp" ]; then
        log_info "✓ $rp 已存在"
    else
        mkdir -p "$(dirname "$fp")"
        curl -# -fL "$SERVER/$rp" -o "$fp" || { log_error "下载 $rp 失败"; exit 1; }
        log_info "✓ $rp 完成"
    fi
done
log_info "✓ settings/ 完成"

# ---------- JDK 下载 ----------
log_info "检查 JDK ..."
JDK_VERSION="21.0.11+10"
JDK_API="https://api.adoptium.net/v3/binary/latest/21/ga/${os_type}/${arch_type}/jdk/hotspot/normal/eclipse"
JDK_ARCHIVE="$JDK_DIR/jdk.tar.gz"

if [ -d "$JDK_DIR/bin" ]; then
    log_info "✓ JDK 已存在"
else
    log_info "下载 JDK 21 ..."
    curl -# -L "$JDK_API" -o "$JDK_ARCHIVE" || { log_error "JDK 下载失败"; exit 1; }
    mkdir -p "$JDK_DIR"
    if [ "$os_type" = "windows" ]; then
        unzip -q "$JDK_ARCHIVE" -d "$JDK_DIR" && mv "$JDK_DIR"/jdk-*/* "$JDK_DIR/" && rmdir "$JDK_DIR"/jdk-* 2>/dev/null
    else
        tar -xzf "$JDK_ARCHIVE" -C "$JDK_DIR" --strip-components=1
    fi
    [ $? -ne 0 ] && { log_error "JDK 解压失败"; exit 1; }
    rm -f "$JDK_ARCHIVE"
    log_info "✓ JDK 安装完成"
fi

# ---------- 安装 matrix CLI ----------
cat > "$CLI_DIR/matrix" << 'EOF'
#!/bin/bash
INSTALL_DIR="$HOME/.matrix/client"
case "$1" in
    status)
        if [ -f "$INSTALL_DIR/bin/app.pid" ]; then
            kill -0 $(cat "$INSTALL_DIR/bin/app.pid") 2>/dev/null && echo "✓ 运行中" || echo "✗ 未运行"
        else
            echo "✗ 未运行"
        fi
        ;;
    logs)
        tail -f "$INSTALL_DIR/logs/info/info.log" 2>/dev/null || echo "日志文件不存在"
        ;;
    start)
        bash "$INSTALL_DIR/bin/start.sh"
        ;;
    stop)
        bash "$INSTALL_DIR/bin/stop.sh"
        ;;
    restart)
        bash "$INSTALL_DIR/bin/restart.sh"
        ;;
    update)
        echo "正在升级 matrix ..."
        bash "$INSTALL_DIR/bin/stop.sh" 2>/dev/null
        if [ -f "$INSTALL_DIR/config/server.url" ]; then
            SERVER=$(cat "$INSTALL_DIR/config/server.url")
            echo "下载最新 JAR ..."
            curl -# -fL "$SERVER/matrix-client.jar" -o "$INSTALL_DIR/matrix-client.jar" || {
                echo "下载 JAR 失败，更新中止"
                exit 1
            }
            echo "下载最新 bin 脚本 ..."
            for f in start.sh stop.sh restart.sh; do
                curl -# -fL "$SERVER/bin/$f" -o "$INSTALL_DIR/bin/$f" || echo "下载 $f 失败，跳过"
            done
            chmod +x "$INSTALL_DIR/bin/"*.sh
            echo "升级完成，正在重启服务 ..."
            bash "$INSTALL_DIR/bin/start.sh"
        else
            echo "错误: 找不到 server.url，无法更新"
            exit 1
        fi
        ;;
    uninstall)
        bash "$INSTALL_DIR/bin/stop.sh" 2>/dev/null
        rm -rf "$INSTALL_DIR" "$HOME/.matrix/jdk21" "$HOME/.local/bin/matrix"
        echo "已卸载"
        ;;
    *)
        echo "用法: matrix {start|stop|restart|status|logs|update|uninstall}"
        ;;
esac
EOF
chmod +x "$CLI_DIR/matrix"
log_info "✓ matrix 已安装"

# ---------- 配置 PATH（核心修复） ----------
PATH_LINE='export PATH="$PATH:$HOME/.local/bin"'

for profile in "$HOME/.bash_profile" "$HOME/.bashrc"; do
    if ! grep -qxF "$PATH_LINE" "$profile" 2>/dev/null; then
        echo "$PATH_LINE" >> "$profile"
        log_info "✓ 已写入 $profile"
    else
        log_info "✓ $profile 已包含 PATH 配置"
    fi
done

export PATH="$PATH:$CLI_DIR"
log_info "✓ 当前 PATH 已更新"

# ---------- 启动服务 ----------
log_info "启动 matrix ..."
cd "$INSTALL_DIR" && bash "$INSTALL_DIR/bin/start.sh"

# ---------- 完成提示 ----------
echo ""
echo "=============================================="
echo " ✓ matrix 安装完成!"
echo "=============================================="
echo "安装目录: $INSTALL_DIR"
echo "JDK 目录: $JDK_DIR"
echo ""
echo "已配置环境变量的文件:"
get_profile_files | tr ' ' '\n' | while read -r f; do [ -f "$f" ] && echo "  • $f"; done
echo ""
echo "可用命令 (新开终端后直接使用）:"
echo " matrix start       启动服务"
echo " matrix stop        停止服务"
echo " matrix restart     重启服务"
echo " matrix logs        查看日志"
echo " matrix status      查看运行状态"
echo " matrix update      更新升级"
echo " matrix uninstall   卸载"
echo ""
echo "如果新终端仍然找不到 matrix，请执行:"
echo "  source ~/.bash_profile   (bash 用户)"
echo "  source ~/.zshrc          (zsh 用户)"
echo "=============================================="