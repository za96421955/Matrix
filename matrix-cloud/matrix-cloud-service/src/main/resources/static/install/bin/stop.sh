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

log_info "正在停止服务..."

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
        log_info "服务已停止 (PID: $PID)"
        exit 0
    else
        log_info "PID 文件中的进程已不存在，清理文件"
        rm -f "$PID_FILE"
    fi
fi

# 2. PID 文件失效时，通过进程名兜底清理（与原逻辑类似但更精准）
JAR_PATTERN="matrix-client-.*\.jar"
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