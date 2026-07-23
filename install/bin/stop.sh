#!/bin/bash

log_info() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] $1"
}

log_warn() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] $1"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $1"
}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$SCRIPT_DIR/app.pid"
WEBUI_PID_FILE="$SCRIPT_DIR/webui.pid"

# ============================================================
# 停止 WebUI HTTP 服务器
# ============================================================

INSTALL_BIN_DIR="$HOME/.matrix/local/bin"

# 三层查找：PID文件（当前目录）-> PID文件（安装目录）-> 进程名兜底
WEBUI_STOPPED=false
WEBUI_PID=""

# 第一层：当前目录 PID 文件
if [ -f "$WEBUI_PID_FILE" ]; then
    WEBUI_PID=$(cat "$WEBUI_PID_FILE")
fi

# 第二层：安装目录 PID 文件
if [ -z "$WEBUI_PID" ] && [ -f "$INSTALL_BIN_DIR/webui.pid" ]; then
    WEBUI_PID=$(cat "$INSTALL_BIN_DIR/webui.pid")
    WEBUI_PID_FILE="$INSTALL_BIN_DIR/webui.pid"
fi

# 第三层：通过进程名查找（匹配 http.server / SimpleHTTPServer / npx serve）
if [ -z "$WEBUI_PID" ] || ! kill -0 "$WEBUI_PID" 2>/dev/null; then
    WEBUI_PID=$(pgrep -f 'http\.server|SimpleHTTPServer|serve.*-s' 2>/dev/null | head -1)
fi

if [ -n "$WEBUI_PID" ] && kill -0 "$WEBUI_PID" 2>/dev/null; then
    log_info "正在停止 WebUI (PID: $WEBUI_PID) ..."
    kill -TERM "$WEBUI_PID" 2>/dev/null
    for i in $(seq 1 5); do
        if ! kill -0 "$WEBUI_PID" 2>/dev/null; then
            break
        fi
        sleep 1
    done
    if kill -0 "$WEBUI_PID" 2>/dev/null; then
        log_warn "WebUI 进程未退出，强制终止"
        kill -9 "$WEBUI_PID" 2>/dev/null
    fi
    log_info "WebUI 已停止"
    WEBUI_STOPPED=true
fi

# 清理所有可能的 PID 文件
for pid_file in "$INSTALL_BIN_DIR/webui.pid" "$SCRIPT_DIR/webui.pid"; do
    if [ -f "$pid_file" ]; then
        rm -f "$pid_file"
    fi
done

if [ "$WEBUI_STOPPED" = false ]; then
    log_info "WebUI 未在运行"
fi

# ============================================================
# 停止后端 Java 服务
# ============================================================
log_info "正在停止后端服务..."

# 1. 优先使用 PID 文件
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        log_info "向进程组 -$PID 发送 TERM 信号..."
        kill -TERM -"$PID" 2>/dev/null || kill -TERM "$PID" 2>/dev/null
        for i in $(seq 1 10); do
            if ! kill -0 "$PID" 2>/dev/null; then
                break
            fi
            sleep 1
        done
        if kill -0 "$PID" 2>/dev/null; then
            log_warn "进程未退出，强制终止"
            kill -9 -"$PID" 2>/dev/null || kill -9 "$PID" 2>/dev/null
            sleep 1
        fi
        rm -f "$PID_FILE"
        log_info "后端服务已停止 (PID: $PID)"
        exit 0
    else
        log_info "PID 文件中的进程已不存在，清理文件"
        rm -f "$PID_FILE"
    fi
fi

# 2. PID 文件失效时，通过进程名兜底清理
JAR_PATTERN="matrix-local-.*\.jar"
PIDS=$(pgrep -f "java.*$JAR_PATTERN" 2>/dev/null)
if [ -z "$PIDS" ]; then
    PIDS=$(ps aux | grep "[j]ava.*$JAR_PATTERN" | awk '{print $2}')
fi

if [ -n "$PIDS" ]; then
    log_warn "通过进程名找到残留进程，正在清理..."
    for p in $PIDS; do
        kill -TERM "$p" 2>/dev/null
        sleep 1
        kill -9 "$p" 2>/dev/null
        log_info "已停止残留进程 PID: $p"
    done
    exit 0
fi

log_warn "未找到运行中的服务"
