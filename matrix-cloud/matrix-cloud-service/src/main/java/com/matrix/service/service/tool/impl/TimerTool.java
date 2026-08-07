package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.ToolOperation;
import com.matrix.common.enums.RedisKey;
import com.matrix.common.enums.TimerStatus;
import com.matrix.service.cache.ServiceCache;
import com.matrix.service.service.tool.AbstractTool;
import jakarta.annotation.Resource;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 定时任务工具
 * <p>可创建定时执行的自动化任务。支持设置启动时间、执行次数、执行间隔。任务通过AI Agent在指定时间自动执行。</p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.tools.timer", havingValue = "true")
public class TimerTool extends AbstractTool<TimerTool.Request> {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ServiceCache serviceCache;

    @Override
    /** 获取组件名称 */
    public String name() {
        return "timer";
    }
    /** 获取Name属性值 */
    public static String getName() {
        return new TimerTool().name();
    }

    @Override
    /** 获取组件描述 */
    public String description() {
        return "可创建定时执行的自动化任务。支持设置启动时间、执行次数、执行间隔。";
    }

    @Override
    /** 获取请求参数类型 */
    public Class<Request> requestType() {
        return Request.class;
    }

    @Override
    /** 执行工具核心逻辑 */
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // ClientId 检查
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }
        // 根据 option 分发
        String option = request.getOption();
        if (ToolOperation.CREATE.equalsIgnoreCase(option)) {
            return createTask(userId, sessionId, request);
        } else if (ToolOperation.LIST.equalsIgnoreCase(option)) {
            return listTasks(userId);
        } else if (ToolOperation.DELETE.equalsIgnoreCase(option)) {
            return deleteTask(userId, request);
        } else {
            return Flux.just("无效的操作类型: " + option + ", 可选值: create, list, delete");
        }
    }

    /**
     * @description 创建定时任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Flux<String> createTask(Long userId, Long sessionId, Request request) {
        // 校验 executeCount
        if (request.getExecuteCount() == null || request.getExecuteCount() == 0) {
            return Flux.just("创建失败: executeCount 不能为 0");
        }
        // 校验 title
        if (StringUtils.isBlank(request.getTitle())) {
            return Flux.just("创建失败: title 不能为空");
        }
        // 校验 content
        if (StringUtils.isBlank(request.getContent())) {
            return Flux.just("创建失败: content 不能为空");
        }
        // 校验 intervalSeconds
        if (request.getExecuteCount() > 1 && (request.getIntervalSeconds() == null || request.getIntervalSeconds() <= 0)) {
            return Flux.just("创建失败: intervalSeconds 必须大于 0");
        }

        // 解析 startTime
        long startTimestamp;
        if (StringUtils.isNotBlank(request.getStartTime())) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(request.getStartTime(), DTF);
                startTimestamp = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                // 格式转换失败，立即执行
                startTimestamp = System.currentTimeMillis();
            }
        } else {
            startTimestamp = System.currentTimeMillis();
        }

        // 检查任务是否已存在
        String taskKey = RedisKey.TIMER_USER_TASKS.generateKey(userId);
        Object existing = serviceCache.getHash().get(taskKey, request.getTitle());
        if (existing != null) {
            return Flux.just("创建失败: 任务标题 '" + request.getTitle() + "' 已存在");
        }

        // 构建任务信息
        TimerTaskInfo taskInfo = TimerTaskInfo.builder()
                .userId(userId)
                .sessionId(sessionId)
                .title(request.getTitle())
                .content(request.getContent())
                .startTime(request.getStartTime())
                .startTimestamp(startTimestamp)
                .executeCount(request.getExecuteCount())
                .intervalSeconds(request.getIntervalSeconds())
                .executedCount(0)
                .nextExecuteTime(startTimestamp)
                .status(TimerStatus.ACTIVE.getValue())
                .createTime(System.currentTimeMillis())
                .build();

        // 写入 Redis Hash
        serviceCache.getHash().put(taskKey, request.getTitle(), JSONObject.toJSONString(taskInfo),
                RedisKey.TIMER_USER_TASKS.getTtl());
        // 将 userId 加入用户列表 Set
        serviceCache.getSet().add(RedisKey.TIMER_USER_LIST.generateKey(), String.valueOf(userId),
                RedisKey.TIMER_USER_LIST.getTtl());

        return Flux.just("定时任务创建成功\n- 标题: " + request.getTitle()
                + "\n- 启动时间: " + (StringUtils.isNotBlank(request.getStartTime()) ? request.getStartTime() : "立即")
                + "\n- 执行次数: " + (request.getExecuteCount() == -1 ? "无限循环" : String.valueOf(request.getExecuteCount()))
                + "\n- 执行间隔: " + request.getIntervalSeconds() + "秒");
    }

    /**
     * @description 查看定时任务列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Flux<String> listTasks(Long userId) {
        String taskKey = RedisKey.TIMER_USER_TASKS.generateKey(userId);
        Map<String, String> entries = serviceCache.getHash().getAll(taskKey);
        if (entries.isEmpty()) {
            return Flux.just("暂无定时任务");
        }

        StringBuilder sb = new StringBuilder("定时任务列表:\n");
        int idx = 1;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String title = entry.getKey();
            TimerTaskInfo info = JSONObject.parseObject(entry.getValue(), TimerTaskInfo.class);
            if (info == null) {
                continue;
            }
            sb.append(idx++).append(". ").append(title)
                    .append(" [状态: ").append(info.getStatus()).append("]")
                    .append(" [已执行: ").append(info.getExecutedCount()).append("次")
                    .append("/").append(info.getExecuteCount() == null || info.getExecuteCount() == -1 ? "无限" : info.getExecuteCount()).append("次]")
                    .append(" [下次执行: ").append(formatTime(info.getNextExecuteTime())).append("]")
                    .append("\n");
        }
        return Flux.just(sb.toString());
    }

    /**
     * @description 删除定时任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Flux<String> deleteTask(Long userId, Request request) {
        if (StringUtils.isBlank(request.getTitle())) {
            return Flux.just("删除失败: title 不能为空");
        }
        String taskKey = RedisKey.TIMER_USER_TASKS.generateKey(userId);
        serviceCache.getHash().remove(taskKey, request.getTitle());
        // 清理分布式锁
        String lockKey = RedisKey.LOCK_KEY_PREFIX.generateKey(userId, request.getTitle());
        serviceCache.delete(lockKey);
        // 如果该用户没有其他任务，从 Set 中移除 userId
        Long size = serviceCache.getHash().size(taskKey);
        if (size == null || size == 0) {
            serviceCache.getSet().remove(RedisKey.TIMER_USER_LIST.generateKey(), String.valueOf(userId));
        }
        return Flux.just("定时任务 '" + request.getTitle() + "' 已删除");
    }

    /**
     * @description 格式化时间戳
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String formatTime(Long timestamp) {
        if (timestamp == null || timestamp <= 0) {
            return "待定";
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault());
        return ldt.format(DTF);
    }

    /**
     * @description 定时任务信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimerTaskInfo {
        /** 所属用户 */
        private Long userId;
        /** 所属会话 */
        private Long sessionId;
        /** 任务标题 */
        private String title;
        /** 任务内容 */
        private String content;
        /** 启动时间（原始格式） */
        private String startTime;
        /** 启动时间戳 */
        private Long startTimestamp;
        /** 执行次数（-1无限循环, >=1执行N次） */
        private Integer executeCount;
        /** 执行间隔秒数 */
        private Integer intervalSeconds;
        /** 已执行次数 */
        private Integer executedCount;
        /** 下次执行时间戳 */
        private Long nextExecuteTime;
        /** 任务状态: ACTIVE-进行中, COMPLETED-已完成 */
        private String status;
        /** 创建时间戳 */
        private Long createTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("操作类型: create-创建, list-查看列表, delete-删除。")
        private String option;

        @Description("任务标题，唯一标识。")
        private String title;

        @Description("待执行的任务内容。")
        private String content;

        @Description("首次启动时间, 格式: yyyy-MM-dd HH:mm:ss。")
        private String startTime;

        @Description("执行次数: -1无限循环, >=1执行N次。")
        private Integer executeCount;

        @Description("执行间隔秒数。")
        private Integer intervalSeconds;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


