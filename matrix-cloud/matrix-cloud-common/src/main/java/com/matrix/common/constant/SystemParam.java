package com.matrix.common.constant;

/**
 * 系统参数（魔法数字与超时参数）
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface SystemParam {

    /** MQTT QoS */
    int MQTT_QOS = 1;

    /** 任务超时（秒） */
    long TASK_TIMEOUT = 60;

    /** 指令超时（秒） */
    long COMMAND_TIMEOUT = 10;

    /** 授权等待超时倍数 */
    long AUTH_WAIT_TIMEOUT_MULTIPLIER = 10;

    /** 任务执行等待超时倍数 */
    long TASK_WAIT_TIMEOUT_MULTIPLIER = 3;

    /** 对话缓存过期时间（秒） */
    long CONVERSATION_CACHE_EXPIRE_SECONDS = 1;

    /** 对话缓存最大容量 */
    long CONVERSATION_CACHE_MAX_SIZE = 30000;

    /** 系统会话 ID */
    long SYSTEM_SESSION_ID = -1L;

    /** 缓存 Key 分隔符 */
    String CACHE_KEY_SEPARATOR = "@@@";

    /** 心跳限流（毫秒） */
    long HEARTBEAT_RATE_LIMIT_MS = 10000;

    /** 离线检查周期（毫秒） */
    long OFFLINE_CHECK_DELAY_MS = 30000;

    /** 心跳间隔默认值（秒） */
    String KEEP_ALIVE_DEFAULT = "60";

    /** 离线判定系数默认值 */
    String OFFLINE_THRESHOLD_DEFAULT = "1.5";

    /** 定时任务扫描周期（毫秒） */
    long TIMER_SCAN_DELAY_MS = 5000;

    /** 任务锁前缀 */
    String TASK_LOCK_PREFIX = "lock:task:";

    /** 任务锁时长（秒） */
    long TASK_LOCK_SECONDS = 10;

    /** 停止重试休眠（毫秒） */
    long STOP_RETRY_DELAY_MS = 3000;

    /** 默认分页页码 */
    int DEFAULT_PAGE_NUM = 1;

    /** 默认分页大小 */
    int DEFAULT_PAGE_SIZE = 1000;

    /** 模型最大 token */
    int MAX_TOKENS = 4096;

}


