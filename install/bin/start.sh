#!/bin/bash
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

log_info() {
	echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] $1"
}

log_warn() {
	echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] $1"
}

log_error() {
	echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $1"
}

SCRIPT_DIR="$HOME/.matrix/local/bin"

# ============================================================
# step1: check ~/.jdks for existing JDK21
# ============================================================
log_info "正在检查JDK环境..."

INSTALLED_JDK_DIR=$(ls -d ~/.jdks/jdk-21* 2>/dev/null | head -1)
if [ -n "$INSTALLED_JDK_DIR" ]; then
	if [ -d "$INSTALLED_JDK_DIR/Contents/Home" ]; then
		INSTALLED_JDK_DIR="$INSTALLED_JDK_DIR/Contents/Home"
	fi
	if [ -f "$INSTALLED_JDK_DIR/bin/java" ]; then
		export JAVA_HOME="$INSTALLED_JDK_DIR"
		export PATH="$JAVA_HOME/bin:$PATH"
		log_info "✓ JDK21已安装（跳过安装步骤）"
	else
		log_warn "~/.jdks目录不完整，将重新安装"
		INSTALLED_JDK_DIR=""
	fi
fi

# ============================================================
# step2: check system java -version
# ============================================================
if [ -z "$INSTALLED_JDK_DIR" ]; then
	JAVA_VERSION_OUTPUT=$(java -version 2>&1)
	JDK_VERSION=$(echo "$JAVA_VERSION_OUTPUT" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)

	if echo "$JAVA_VERSION_OUTPUT" | grep -qE '"21"|"21\.'; then
		log_info "✓系统JDK版本检查通过"
	else
		log_warn "需要JDK21，当前版本为 ${JDK_VERSION:-未知}"

		# ============================================================
		# step3: install JDK21
		# ============================================================
		OS=$(uname -s)
		ARCH=$(uname -m)
		log_info "系统: ${OS} ${ARCH}"

		JDK_PACKAGE_DIR="$(cd "$SCRIPT_DIR/../../jdk21" && pwd)"

		if [ "$OS" = "Darwin" ]; then
			if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then
				INSTALLER_FILE=$(ls "$JDK_PACKAGE_DIR"/*aarch64_mac*.tar.gz 2>/dev/null | head -1)
				[ -z "$INSTALLER_FILE" ] && INSTALLER_FILE=$(ls "$JDK_PACKAGE_DIR"/*mac*aarch64*.tar.gz 2>/dev/null | head -1)
			else
				INSTALLER_FILE=$(ls "$JDK_PACKAGE_DIR"/*x64_mac*.tar.gz 2>/dev/null | head -1)
				[ -z "$INSTALLER_FILE" ] && INSTALLER_FILE=$(ls "$JDK_PACKAGE_DIR"/*mac*x64*.tar.gz 2>/dev/null | head -1)
			fi
		elif [ "$OS" = "Linux" ]; then
			if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then
				INSTALLER_FILE=$(ls "$JDK_PACKAGE_DIR"/*aarch64_linux*.tar.gz 2>/dev/null | head -1)
				[ -z "$INSTALLER_FILE" ] && INSTALLER_FILE=$(ls "$JDK_PACKAGE_DIR"/*linux*aarch64*.tar.gz 2>/dev/null | head -1)
			else
				INSTALLER_FILE=$(ls "$JDK_PACKAGE_DIR"/*x64_linux*.tar.gz 2>/dev/null | head -1)
				[ -z "$INSTALLER_FILE" ] && INSTALLER_FILE=$(ls "$JDK_PACKAGE_DIR"/*linux*x64*.tar.gz 2>/dev/null | head -1)
			fi
		else
			log_error "不支持的操作系统: ${OS}"
			exit 1
		fi

		if [ -z "$INSTALLER_FILE" ]; then
			log_error "在 ${JDK_PACKAGE_DIR}中找不到匹配 (${OS} ${ARCH})的 JDK21安装包"
			exit 1
		fi

		log_info "使用本地安装包: $(basename "$INSTALLER_FILE")"

		mkdir -p ~/.jdks
		tar -xzf "$INSTALLER_FILE" -C ~/.jdks/
		if [ $? -ne 0 ]; then
			log_error "解压失败"
			exit 1
		fi

		INSTALLED_JDK_DIR=$(ls -d ~/.jdks/jdk-21* 2>/dev/null | head -1)
		if [ -z "$INSTALLED_JDK_DIR" ]; then
			log_error "解压后找不到JDK目录"
			exit 1
		fi

		if [ -d "$INSTALLED_JDK_DIR/Contents/Home" ]; then
			INSTALLED_JDK_DIR="$INSTALLED_JDK_DIR/Contents/Home"
		fi

		if [ ! -f "$INSTALLED_JDK_DIR/bin/java" ]; then
			log_error "JDK目录中找不到 bin/java，请检查安装包"
			exit 1
		fi

		# 移除 macOS 隔离属性
		if [ "$OS" = "Darwin" ]; then
			xattr -r -d com.apple.quarantine "$INSTALLED_JDK_DIR" 2>/dev/null
		fi

		if [ "$OS" = "Darwin" ]; then
			SHELL_RC="$HOME/.zshrc"
			[ ! -f "$SHELL_RC" ] && SHELL_RC="$HOME/.bash_profile"
		else
			SHELL_RC="$HOME/.bashrc"
		fi

		if ! grep -q "# OpenJDK21" "$SHELL_RC" 2>/dev/null; then
			{
			echo ""
			echo "# OpenJDK21"
			echo "export JAVA_HOME=$INSTALLED_JDK_DIR"
			echo 'export PATH=$JAVA_HOME/bin:$PATH'
			} >> "$SHELL_RC"
		fi

		export JAVA_HOME="$INSTALLED_JDK_DIR"
		export PATH="$JAVA_HOME/bin:$PATH"

		log_info "✓ JDK21已安装到 ~/.jdks"
	fi
fi

# ============================================================
# step4: verify JDK21
# ============================================================
JAVA_CMD="${JAVA_HOME}/bin/java"
if [ ! -f "$JAVA_CMD" ]; then
	JAVA_CMD="java"
fi

JAVA_VERSION_OUTPUT_NEW=$("$JAVA_CMD" -version 2>&1)
if echo "$JAVA_VERSION_OUTPUT_NEW" | grep -qE '"21"|"21\.'; then
	log_info "✓ JDK版本检查通过"
else
	log_error "JDK安装失败，请手动安装"
	exit 1
fi

# ============================================================
# step5: start service
# ============================================================
log_info "正在准备启动服务..."

PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

JAR_FILE=$(find "$PROJECT_ROOT" -maxdepth 1 -name "matrix-local*.jar" 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
	log_error "未找到JAR文件"
	exit 1
fi

# JAR 完整性校验
if ! unzip -tqq "$JAR_FILE" >/dev/null 2>&1; then
	log_error "JAR文件损坏或无效: $(basename "$JAR_FILE")"
	log_error "请重新下载或执行: matrix update"
	exit 1
fi

# ----------------------------------------------------------
# 停止旧进程（优雅停机 + 强制兜底）
# ----------------------------------------------------------
if [ -f "$SCRIPT_DIR/app.pid" ]; then
	OLD_PID=$(cat "$SCRIPT_DIR/app.pid")
	if kill -0 "$OLD_PID" 2>/dev/null; then
		log_info "检测到旧进程 (PID: $OLD_PID)，正在停止..."
		# 向进程组发送 TERM 信号
		kill -TERM -"$OLD_PID" 2>/dev/null || kill -TERM "$OLD_PID" 2>/dev/null
		for i in $(seq 1 10); do
			if ! kill -0 "$OLD_PID" 2>/dev/null; then
				break
			fi
			sleep 1
		done
		if kill -0 "$OLD_PID" 2>/dev/null; then
			log_warn "旧进程未在10秒内退出，强制终止"
			kill -9 -"$OLD_PID" 2>/dev/null || kill -9 "$OLD_PID" 2>/dev/null
			sleep 1
		fi
	else
		log_info "旧PID文件存在但进程已不存在，清理文件"
	fi
	rm -f "$SCRIPT_DIR/app.pid"
fi

# 如还有残留的 executor 进程，兜底清理（防止 PID 文件丢失或记录错误）
REMAIN_PIDS=$(pgrep -f "java.*$(basename "$JAR_FILE")" 2>/dev/null)
if [ -n "$REMAIN_PIDS" ]; then
	log_warn "发现残留 executor 进程，强制清理..."
	for p in $REMAIN_PIDS; do
		kill -9 "$p" 2>/dev/null
	done
	sleep 1
fi

# ----------------------------------------------------------
# 启动服务
# ----------------------------------------------------------
mkdir -p "$PROJECT_ROOT/logs"

log_info "正在启动服务..."
cd "$PROJECT_ROOT" && nohup "$JAVA_CMD" -jar "$JAR_FILE" \
	-Djava.net.preferIPv4Stack=true \
	-Djava.net.soReuseaddr=true \
	> "$PROJECT_ROOT/logs/app.log" 2>&1 &

# 等待进程稳定后获取真实 PID
sleep 3
REAL_PID=$(pgrep -f "java.*$(basename "$JAR_FILE")" 2>/dev/null | head -1)
if [ -z "$REAL_PID" ]; then
	# 兼容不支持 pgrep 的系统
	REAL_PID=$(ps aux | grep "[j]ava.*$(basename "$JAR_FILE")" | awk '{print $2}' | head -1)
fi

if [ -z "$REAL_PID" ]; then
	log_error "启动失败，无法找到 Java 进程"
	log_error "--- 最近日志 ($PROJECT_ROOT/logs/app.log) ---"
	if [ -f "$PROJECT_ROOT/logs/app.log" ]; then
		while IFS= read -r line; do
			log_error "  $line"
		done < <(tail -20 "$PROJECT_ROOT/logs/app.log")
	fi
	log_error "--- 日志结束 ---"
	exit 1
fi

echo $REAL_PID > "$SCRIPT_DIR/app.pid"
log_info "✓ 后端服务启动成功，PID: $REAL_PID"

# ============================================================
# step6: start WebUI proxy server
# ============================================================
WEBUI_PORT=10908
WEBUI_PID_FILE="$SCRIPT_DIR/webui.pid"
PROXY_SCRIPT="$SCRIPT_DIR/proxy_server.py"
BACKEND_PORT=10906

if [ -d "$PROJECT_ROOT/webui" ] && [ -f "$PROJECT_ROOT/webui/index.html" ]; then
	log_info "正在启动 WebUI 代理服务器 (端口 $WEBUI_PORT, 后端 :$BACKEND_PORT/v1) ..."

	# 如果已有 webui 进程，先停止
	if [ -f "$WEBUI_PID_FILE" ]; then
		OLD_WEBUI_PID=$(cat "$WEBUI_PID_FILE")
		kill "$OLD_WEBUI_PID" 2>/dev/null
		sleep 1
		rm -f "$WEBUI_PID_FILE"
	fi

	if command -v python3 &>/dev/null; then
		MATRIX_WEBUI_DIR="$PROJECT_ROOT/webui" \
		MATRIX_BACKEND_PORT="$BACKEND_PORT" \
		MATRIX_WEBUI_PORT="$WEBUI_PORT" \
		nohup python3 "$PROXY_SCRIPT" \
			> "$PROJECT_ROOT/logs/webui.log" 2>&1 &
		WEBUI_PID=$!
		echo $WEBUI_PID > "$WEBUI_PID_FILE"
		log_info "✓ WebUI 启动成功 (http://localhost:$WEBUI_PORT，API 代理至 :$BACKEND_PORT)"
	else
		log_warn "未找到 Python3，WebUI 无法自动启动"
		log_warn "请手动执行: cd $PROJECT_ROOT/webui && python3 -m http.server $WEBUI_PORT"
		log_warn "注意: 此方式 API 请求会 404，建议安装 Python3 后使用代理脚本"
	fi
else
	log_info "WebUI 目录不存在或缺少 index.html，跳过 WebUI 启动"
	log_info "如需 WebUI，请执行: matrix update"
fi

log_info "✓ 全部启动完成"
echo ""
echo "后端服务: http://localhost:$BACKEND_PORT"
echo "WebUI:    http://localhost:$WEBUI_PORT (API 自动代理至 :$BACKEND_PORT/v1)"
echo ""
