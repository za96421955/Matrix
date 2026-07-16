-- ========================================
-- Matrix Local Database Schema (SQLite)
-- 所有持久化使用 SQLite，零外部依赖
-- ========================================

-- 用户信息表
CREATE TABLE IF NOT EXISTS tbl_user_info (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    username        TEXT    NOT NULL UNIQUE,
    password_hash   TEXT    NOT NULL,
    auth_level      INTEGER NOT NULL DEFAULT 0,
    email           TEXT,
    phone           TEXT,
    create_time     TEXT    DEFAULT (datetime('now','localtime')),
    creator         TEXT,
    update_time     TEXT    DEFAULT (datetime('now','localtime')),
    updator         TEXT,
    version_num     INTEGER DEFAULT 0,
    is_deleted      INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_user_info_username ON tbl_user_info(username);
CREATE INDEX IF NOT EXISTS idx_user_info_phone ON tbl_user_info(phone);

-- 终端表
CREATE TABLE IF NOT EXISTS tbl_client_info (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER NOT NULL,
    client_id       TEXT    NOT NULL,
    type            TEXT,
    os_info         TEXT,
    status          TEXT    DEFAULT 'offline',
    secret          TEXT,
    last_heartbeat  TEXT,
    create_time     TEXT    DEFAULT (datetime('now','localtime')),
    creator         TEXT,
    update_time     TEXT    DEFAULT (datetime('now','localtime')),
    updator         TEXT,
    version_num     INTEGER DEFAULT 0,
    is_deleted      INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_client_info_user_id ON tbl_client_info(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_client_info_user_client ON tbl_client_info(user_id, client_id);

-- 会话表
CREATE TABLE IF NOT EXISTS tbl_session_info (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER NOT NULL,
    title           TEXT,
    agent           TEXT,
    auth_level      INTEGER DEFAULT 0,
    create_time     TEXT    DEFAULT (datetime('now','localtime')),
    creator         TEXT,
    update_time     TEXT    DEFAULT (datetime('now','localtime')),
    updator         TEXT,
    version_num     INTEGER DEFAULT 0,
    is_deleted      INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_session_info_user_id ON tbl_session_info(user_id);

-- 消息表
CREATE TABLE IF NOT EXISTS tbl_message_info (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id           INTEGER NOT NULL,
    session_id        INTEGER NOT NULL,
    role              TEXT    NOT NULL,
    content           TEXT,
    reasoning_content TEXT,
    tool_calls        TEXT,
    tool_call_id      TEXT,
    create_time       TEXT    DEFAULT (datetime('now','localtime')),
    creator           TEXT,
    update_time       TEXT    DEFAULT (datetime('now','localtime')),
    updator           TEXT,
    version_num       INTEGER DEFAULT 0,
    is_deleted        INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_message_info_session_id ON tbl_message_info(session_id);
CREATE INDEX IF NOT EXISTS idx_message_info_user_id ON tbl_message_info(user_id);

-- 用户访问密钥表
CREATE TABLE IF NOT EXISTS tbl_user_api_key (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER NOT NULL,
    api_key         TEXT    NOT NULL UNIQUE,
    status          TEXT    DEFAULT 'enabled',
    create_time     TEXT    DEFAULT (datetime('now','localtime')),
    creator         TEXT,
    update_time     TEXT    DEFAULT (datetime('now','localtime')),
    updator         TEXT,
    version_num     INTEGER DEFAULT 0,
    is_deleted      INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_user_api_key_user_id ON tbl_user_api_key(user_id);
CREATE INDEX IF NOT EXISTS idx_user_api_key_api_key ON tbl_user_api_key(api_key);

-- 任务表
CREATE TABLE IF NOT EXISTS tbl_task_info (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER NOT NULL,
    agent_name      TEXT,
    task_id         TEXT    NOT NULL UNIQUE,
    type            TEXT,
    status          TEXT    DEFAULT 'pending',
    content         TEXT,
    result          TEXT,
    create_time     TEXT    DEFAULT (datetime('now','localtime')),
    creator         TEXT,
    update_time     TEXT    DEFAULT (datetime('now','localtime')),
    updator         TEXT,
    version_num     INTEGER DEFAULT 0,
    is_deleted      INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_task_info_user_id ON tbl_task_info(user_id);
CREATE INDEX IF NOT EXISTS idx_task_info_status ON tbl_task_info(status);

-- ========================================
-- Local 专属表（替代 Redis）
-- ========================================

-- 本地缓存表（替代 Redis KV 存储）
CREATE TABLE IF NOT EXISTS tbl_local_cache (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    cache_key   TEXT    NOT NULL UNIQUE,
    cache_value TEXT,
    expire_at   INTEGER,
    create_time TEXT    DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_local_cache_key ON tbl_local_cache(cache_key);
CREATE INDEX IF NOT EXISTS idx_local_cache_expire ON tbl_local_cache(expire_at);

-- 本地定时任务表（替代 Redis TimerTool）
CREATE TABLE IF NOT EXISTS tbl_local_timer (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id           INTEGER NOT NULL,
    session_id        INTEGER,
    title             TEXT    NOT NULL UNIQUE,
    content           TEXT    NOT NULL,
    start_time        TEXT,
    next_execute_time INTEGER,
    execute_count     INTEGER,
    interval_seconds  INTEGER,
    executed_count    INTEGER DEFAULT 0,
    status            TEXT    DEFAULT 'ACTIVE',
    create_time       INTEGER
);

CREATE INDEX IF NOT EXISTS idx_local_timer_status ON tbl_local_timer(status);
CREATE INDEX IF NOT EXISTS idx_local_timer_next_execute ON tbl_local_timer(next_execute_time);
