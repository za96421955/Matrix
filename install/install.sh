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

# ---------- 下载 URL 常量 ----------
SERVER="https://gitee.com/za96421955/matrix/raw/release/1.0.2/install"
RELEASE_URL="https://gitee.com/za96421955/matrix/releases/download/v1.0.1"

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
INSTALL_DIR="$HOME/.matrix/local"
JDK_DIR="$HOME/.matrix/jdk21"
CLI_DIR="$HOME/.local/bin"

log_info "创建目录..."
mkdir -p "$INSTALL_DIR"/{bin,data,config,settings,logs,webui} "$CLI_DIR" "$JDK_DIR"
log_info "✓ 目录就绪"

# ---------- 保存服务器地址 (供 matrix update 使用) ----------
echo "$SERVER" > "$INSTALL_DIR/config/server.url"
echo "$RELEASE_URL" > "$INSTALL_DIR/config/release.url"
log_info "✓ 服务器地址已保存"

# ---------- 下载 JAR（从 Release 附件下载分卷并合并） ----------
JAR_FILE="$INSTALL_DIR/matrix-local-1.0.1.jar"
TMP_DIR="$INSTALL_DIR/.tmp"

log_info "下载 JAR (分卷文件) ..."
mkdir -p "$TMP_DIR"

# 下载分卷
curl -# -fL "$RELEASE_URL/matrix-local-1.0.1.part.z01" -o "$TMP_DIR/matrix-local-1.0.1.part.z01" || {
    log_error "下载 matrix-local-1.0.1.part.z01 失败"
    exit 1
}
curl -# -fL "$RELEASE_URL/matrix-local-1.0.1.part.zip" -o "$TMP_DIR/matrix-local-1.0.1.part.zip" || {
    log_error "下载 matrix-local-1.0.1.part.zip 失败"
    exit 1
}
log_info "✓ 分卷下载完成"

# 合并分卷为 JAR
log_info "合并分卷为 JAR 文件 ..."
cd "$TMP_DIR" || exit 1

MERGED=false

# 方法1: 使用 7z (直接解压分卷)
if command -v 7z &>/dev/null; then
    log_info "尝试 7z 解压 ..."
    7z x matrix-local-1.0.1.part.zip -o"$INSTALL_DIR" -y >/dev/null 2>&1
    if [ -f "$JAR_FILE" ] && file "$JAR_FILE" | grep -qiE "zip|java|archive"; then
        log_info "✓ 7z 解压成功"
        MERGED=true
    fi
fi

# 方法2: 使用 zip -F 合并分卷后 unzip 解压
if [ "$MERGED" = false ] && command -v zip &>/dev/null; then
    log_info "尝试 zip -F + unzip 合并 ..."
    cp matrix-local-1.0.1.part.z01 matrix-local-1.0.1.z01
    cp matrix-local-1.0.1.part.zip matrix-local-1.0.1.zip
    zip -F matrix-local-1.0.1.zip --out combined.zip >/dev/null 2>&1
    if [ -f combined.zip ] && unzip -tqq combined.zip 2>/dev/null; then
        unzip -o combined.zip -d "$INSTALL_DIR" >/dev/null 2>&1
        if [ -f "$JAR_FILE" ]; then
            log_info "✓ zip -F + unzip 合并成功"
            MERGED=true
        fi
    fi
fi

# 方法3: 使用 cat 合并分卷后 unzip 解压
if [ "$MERGED" = false ] && command -v unzip &>/dev/null; then
    log_info "尝试 cat + unzip 合并 ..."
    cat matrix-local-1.0.1.part.z01 matrix-local-1.0.1.part.zip > combined.zip 2>/dev/null
    if [ -f combined.zip ] && unzip -tqq combined.zip 2>/dev/null; then
        unzip -o combined.zip -d "$INSTALL_DIR" >/dev/null 2>&1
        if [ -f "$JAR_FILE" ]; then
            log_info "✓ cat + unzip 合并成功"
            MERGED=true
        fi
    fi
fi

# 清理临时文件
rm -rf "$TMP_DIR"

if [ "$MERGED" = false ]; then
    log_error "JAR 分卷合并失败，请手动处理："
    log_error "1. 从 https://gitee.com/za96421955/matrix/releases/tag/v1.0.1 下载 matrix-local-1.0.1.part.z01 和 matrix-local-1.0.1.part.zip"
    log_error "2. 将两个文件放在同一目录，使用 7-Zip/WinRAR 解压 matrix-local-1.0.1.part.zip"
    log_error "3. 将得到的 matrix-local-1.0.1.jar 放入 $INSTALL_DIR/"
    log_error "4. 重新运行本安装脚本"
    exit 1
fi

log_info "✓ JAR 就绪: $(ls -lh "$JAR_FILE" | awk '{print $5}')"

# ---------- 下载 bin/ 脚本 ----------
BIN_FILES="proxy_server.py start.sh stop.sh restart.sh"
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

# ---------- 下载 data/ ----------
log_info "下载 data/ ..."
DATA_FILES="schema.sql"
for f in $DATA_FILES; do
    fp="$INSTALL_DIR/data/$f"
    curl -# -fL "$SERVER/data/$f" -o "$fp" || { log_error "下载 $f 失败"; exit 1; }
    log_info "✓ $f 下载完成"
done
log_info "✓ data/ 完成"

# ---------- 下载 settings/ ----------
SETTINGS_FILES=(
    "settings/MEMORY.md"
    "settings/risk-level.yml"
    "settings/skill/query-typhoon/SKILL.md"
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

# ---------- 下载 webui ----------
log_info "下载 webui ..."
WEBUI_ZIP="$INSTALL_DIR/webui/matrix-webui-1.0.1.zip"
curl -# -fL "$RELEASE_URL/matrix-webui-1.0.1.zip" -o "$WEBUI_ZIP" || {
    log_warn "webui 下载失败，可稍后手动下载"
    log_warn "下载地址: $RELEASE_URL/matrix-webui-1.0.1.zip"
}
if [ -f "$WEBUI_ZIP" ]; then
    log_info "解压 webui ..."
    unzip -o "$WEBUI_ZIP" -d "$INSTALL_DIR/webui/" >/dev/null 2>&1
    if [ -f "$INSTALL_DIR/webui/dist/index.html" ]; then
        # 将 dist/ 内容上移一级，方便 Python HTTP server 直接 serve
        mv "$INSTALL_DIR/webui/dist/"* "$INSTALL_DIR/webui/" 2>/dev/null
        mv "$INSTALL_DIR/webui/dist/".* "$INSTALL_DIR/webui/" 2>/dev/null
        rm -rf "$INSTALL_DIR/webui/dist" "$WEBUI_ZIP"
        log_info "✓ webui 部署完成"
    else
        log_warn "webui 解压后未找到 dist/index.html，请检查"
    fi
fi

# ---------- JDK 下载 ----------
log_info "检查 JDK ..."
JDK_VERSION="21.0.12"

# Microsoft JDK 下载 URL 映射
case "${os_type}-${arch_type}" in
    linux-x64)
        JDK_URL="https://aka.ms/download-jdk/microsoft-jdk-21.0.12-linux-x64.tar.gz"
        JDK_FILENAME="microsoft-jdk-21.0.12-linux-x64.tar.gz"
        ;;
    linux-aarch64)
        JDK_URL="https://aka.ms/download-jdk/microsoft-jdk-21.0.12-linux-aarch64.tar.gz"
        JDK_FILENAME="microsoft-jdk-21.0.12-linux-aarch64.tar.gz"
        ;;
    darwin-x64)
        JDK_URL="https://aka.ms/download-jdk/microsoft-jdk-21.0.12-macos-x64.tar.gz"
        JDK_FILENAME="microsoft-jdk-21.0.12-macos-x64.tar.gz"
        ;;
    darwin-aarch64)
        JDK_URL="https://aka.ms/download-jdk/microsoft-jdk-21.0.12-macos-aarch64.tar.gz"
        JDK_FILENAME="microsoft-jdk-21.0.12-macos-aarch64.tar.gz"
        ;;
    windows-x64)
        JDK_URL="https://aka.ms/download-jdk/microsoft-jdk-21.0.12-windows-x64.zip"
        JDK_FILENAME="microsoft-jdk-21.0.12-windows-x64.zip"
        ;;
    windows-aarch64)
        JDK_URL="https://aka.ms/download-jdk/microsoft-jdk-21.0.12-windows-aarch64.zip"
        JDK_FILENAME="microsoft-jdk-21.0.12-windows-aarch64.zip"
        ;;
esac

JDK_ARCHIVE="$JDK_DIR/$JDK_FILENAME"

if [ -f "$JDK_ARCHIVE" ]; then
    log_info "✓ JDK 已存在"
else
    log_info "下载 JDK 21 ..."
    curl -# -L "$JDK_URL" -o "$JDK_ARCHIVE" || { log_error "JDK 下载失败"; exit 1; }
    mkdir -p "$JDK_DIR"
    if [ "$os_type" = "windows" ]; then
        unzip -q "$JDK_ARCHIVE" -d "$JDK_DIR" && mv "$JDK_DIR"/jdk-*/* "$JDK_DIR/" 2>/dev/null && rmdir "$JDK_DIR"/jdk-* 2>/dev/null
    else
        tar -xzf "$JDK_ARCHIVE" -C "$JDK_DIR" --strip-components=1
    fi
    [ $? -ne 0 ] && { log_error "JDK 解压失败"; exit 1; }
    log_info "✓ JDK 下载完成"
fi

# ---------- 安装 matrix CLI ----------
cat > "$CLI_DIR/matrix" << 'CLIEOF'
#!/bin/bash
INSTALL_DIR="$HOME/.matrix/local"
WEBUI_PORT=10908

case "$1" in
    status)
        echo "--- 后端服务 ---"
        if [ -f "$INSTALL_DIR/bin/app.pid" ]; then
            kill -0 $(cat "$INSTALL_DIR/bin/app.pid") 2>/dev/null && echo "  ✓ 运行中 (PID: $(cat $INSTALL_DIR/bin/app.pid))" || echo "  ✗ 未运行"
        else
            echo "  ✗ 未运行"
        fi
        echo "--- WebUI ---"
        if [ -f "$INSTALL_DIR/bin/webui.pid" ]; then
            kill -0 $(cat "$INSTALL_DIR/bin/webui.pid") 2>/dev/null && echo "  ✓ 运行中 (http://localhost:$WEBUI_PORT)" || echo "  ✗ 未运行"
        else
            echo "  ✗ 未运行"
        fi
        ;;
    logs)
        tail -f "$INSTALL_DIR/logs/app.log" 2>/dev/null || echo "日志文件不存在"
        ;;
    webui-logs)
        tail -f "$INSTALL_DIR/logs/webui.log" 2>/dev/null || echo "WebUI 日志文件不存在"
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
        if [ -f "$INSTALL_DIR/config/release.url" ]; then
            RELEASE_URL=$(cat "$INSTALL_DIR/config/release.url")
            echo "下载最新 JAR ..."
            TMP_DIR="$INSTALL_DIR/.tmp"
            mkdir -p "$TMP_DIR"
            curl -# -fL "$RELEASE_URL/matrix-local-1.0.1.part.z01" -o "$TMP_DIR/matrix-local-1.0.1.part.z01" || { echo "下载分卷1失败"; exit 1; }
            curl -# -fL "$RELEASE_URL/matrix-local-1.0.1.part.zip" -o "$TMP_DIR/matrix-local-1.0.1.part.zip" || { echo "下载分卷2失败"; exit 1; }
            # 尝试合并
            cd "$TMP_DIR"
            if command -v 7z &>/dev/null; then
                7z x matrix-local-1.0.1.part.zip -o"$INSTALL_DIR" -y >/dev/null 2>&1
            elif command -v zip &>/dev/null; then
                cp matrix-local-1.0.1.part.z01 matrix-local-1.0.1.z01
                cp matrix-local-1.0.1.part.zip matrix-local-1.0.1.zip
                zip -F matrix-local-1.0.1.zip --out combined.zip >/dev/null 2>&1
                unzip -o combined.zip -d "$INSTALL_DIR" >/dev/null 2>&1
            else
                cat matrix-local-1.0.1.part.z01 matrix-local-1.0.1.part.zip > combined.zip 2>/dev/null
                unzip -o combined.zip -d "$INSTALL_DIR" >/dev/null 2>&1
            fi
            rm -rf "$TMP_DIR"
            echo "下载最新 webui ..."
            curl -# -fL "$RELEASE_URL/matrix-webui-1.0.1.zip" -o "$INSTALL_DIR/webui/matrix-webui-1.0.1.zip"
            if [ -f "$INSTALL_DIR/webui/matrix-webui-1.0.1.zip" ]; then
                unzip -o "$INSTALL_DIR/webui/matrix-webui-1.0.1.zip" -d "$INSTALL_DIR/webui/" >/dev/null 2>&1
                mv "$INSTALL_DIR/webui/dist/"* "$INSTALL_DIR/webui/" 2>/dev/null
                mv "$INSTALL_DIR/webui/dist/".* "$INSTALL_DIR/webui/" 2>/dev/null
                rm -rf "$INSTALL_DIR/webui/dist" "$INSTALL_DIR/webui/matrix-webui-1.0.1.zip"
            fi
            echo "下载最新 bin 脚本 ..."
            SERVER=$(cat "$INSTALL_DIR/config/server.url" 2>/dev/null)
            [ -n "$SERVER" ] && for f in start.sh stop.sh restart.sh; do
                curl -# -fL "$SERVER/bin/$f" -o "$INSTALL_DIR/bin/$f" 2>/dev/null || echo "下载 $f 失败，跳过"
            done
            chmod +x "$INSTALL_DIR/bin/"*.sh
            echo "升级完成，正在重启服务 ..."
            bash "$INSTALL_DIR/bin/start.sh"
        else
            echo "错误: 找不到 release.url，无法更新"
            exit 1
        fi
        ;;
    uninstall)
        bash "$INSTALL_DIR/bin/stop.sh" 2>/dev/null
        rm -rf "$INSTALL_DIR" "$HOME/.matrix/jdk21" "$HOME/.local/bin/matrix"
        echo "已卸载"
        ;;
    *)
        echo "用法: matrix {start|stop|restart|status|logs|webui-logs|update|uninstall}"
        echo ""
        echo "WebUI: http://localhost:$WEBUI_PORT"
        ;;
esac
CLIEOF
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
echo "WebUI:    http://localhost:10908"
echo ""
echo "已配置环境变量的文件:"
get_profile_files | tr ' ' '\n' | while read -r f; do [ -f "$f" ] && echo "  • $f"; done
echo ""
echo "可用命令 (新开终端后直接使用）:"
echo " matrix start       启动服务"
echo " matrix stop        停止服务"
echo " matrix restart     重启服务"
echo " matrix logs        查看日志"
echo " matrix webui-logs  查看 WebUI 日志"
echo " matrix status      查看运行状态"
echo " matrix update      更新升级"
echo " matrix uninstall   卸载"
echo ""
echo "如果新终端仍然找不到 matrix，请执行:"
echo "  source ~/.bash_profile   (bash 用户)"
echo "  source ~/.zshrc          (zsh 用户)"
echo "=============================================="