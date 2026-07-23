package com.matrix.common.enums;

import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Redis Key 前缀枚举
 * 格式：matrix:{模块名}:{key}
 */
@Getter
public enum RedisKey {
    /** 用户认证 */
    AUTHORIZATION("matrix:user:authorization:%s", 5 * 60L),
    /** 用户对话 */
    CONVERSATION("matrix:user:conversation:%s:%s", 60 * 60L),

    /** 上下文缓存: 用户模型 */
    MODELS("matrix:context:models:%s", 3 * 60L),
    /** 上下文缓存: 用户智能体 */
    AGENTS("matrix:context:agents:%s", 3 * 60L),
    /** 上下文缓存: 智能体调用栈 */
    AGENT_STACK("matrix:context:agent:stack:%s", 30 * 60L),
    /** 上下文缓存: 用户技能 */
    SKILLS("matrix:context:skills:%s", 3 * 60L),
    /** 上下文缓存: 用户应用 */
    APPS("matrix:context:apps:%s", 3 * 60L),
    /** 上下文缓存: 指令风险等级 - bash */
    RISK_LEVEL_BASH("matrix:context:risk-level:%s:bash", 3 * 60L),
    /** 上下文缓存: 指令风险等级 - tool */
    RISK_LEVEL_TOOL("matrix:context:risk-level:%s:tool", 3 * 60L),
    /** 上下文缓存: 指令风险等级 - agent */
    RISK_LEVEL_AGENT("matrix:context:risk-level:%s:agent", 3 * 60L),
    /** 上下文缓存: 指令风险等级 - skill */
    RISK_LEVEL_SKILL("matrix:context:risk-level:%s:skill", 3 * 60L),
    /** 上下文缓存: 指令风险等级 - app */
    RISK_LEVEL_APP("matrix:context:risk-level:%s:app", 3 * 60L),

    /** 任务: 任务信息 */
    TASK_INFO("matrix:task:info:%s", 60 * 60L),
    /** 任务: 任务信息 */
    TASK_WAITING_AUTH_LIST("matrix:task:waiting-auth:%s", 60 * 60L),

    /** 任务模式：任务链 */
    TASK_PATTERN_CHAIN("matrix:task:chain:pattern:%s:%s", 6 * 60 * 60L),
    /** 任务模式：任务图 */
    TASK_PATTERN_GRAPH("matrix:task:graph:pattern:%s:%s", 6 * 60 * 60L),
    /** 任务模式：已完成的任务 */
    TASK_PATTERN_COMPLETE("matrix:task:pattern:complete:%s:%s", 6 * 60 * 60L),

    /** 编程模式：环节编号 */
    CODING_PATTERN_NO("matrix:coding:pattern:no:%s:%s", 6 * 60 * 60L),

    /** 资料模式：环节编号 */
    INFORMATION_PATTERN_NO("matrix:information:pattern:no:%s:%s", 6 * 60 * 60L),

    /** 定时器: 有定时任务的用户列表 */
    TIMER_USER_LIST("matrix:timer:user-list", -1L),
    /** 定时器: 用户定时任务集合 */
    TIMER_USER_TASKS("matrix:timer:user:%s", -1L),
    /** 定时器: 分布式锁 */
    LOCK_KEY_PREFIX("matrix:timer:lock:%s:%s", 10 * 60L),

    ;
    
    /**
     * Key 前缀模板（包含 %s 占位符）
     */
    private final String prefix;
    
    /**
     * 默认 TTL（秒）
     */
    private final Long ttl;
    
    RedisKey(String prefix, Long ttl) {
        this.prefix = prefix;
        this.ttl = ttl;
    }
    
    /**
     * 生成完整 Key
     * @param params 占位符参数
     * @return 完整 Key
     */
    public String generateKey(Object... params) {
        if (ArrayUtils.isEmpty(params)) {
            return this.prefix;
        }
        return String.format(this.prefix, params);
    }

}
