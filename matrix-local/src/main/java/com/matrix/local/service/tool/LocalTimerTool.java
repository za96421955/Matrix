package com.matrix.local.service.tool;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.matrix.common.constant.Constant;
import com.matrix.local.dal.entity.LocalTimer;
import com.matrix.local.dal.mapper.LocalTimerMapper;
import com.matrix.service.service.tool.AbstractTool;
import jakarta.annotation.Resource;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 本地定时任务工具
 * <p>基于 SQLite 持久化的定时任务，替代 Redis 存储方案。
 * 可创建定时执行的自动化任务。支持设置启动时间、执行次数、执行间隔。</p>
 *
 * @author 陈晨
 */
@Slf4j
@Primary
@Component
public class LocalTimerTool extends AbstractTool<LocalTimerTool.Request> {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private LocalTimerMapper localTimerMapper;

    @Override
    public String name() {
        return "timer";
    }
    public static String getName() {
        return new LocalTimerTool().name();
    }

    @Override
    public String description() {
        return "可创建定时执行的自动化任务。支持设置启动时间、执行次数、执行间隔";
    }

    @Override
    public Class<Request> requestType() {
        return Request.class;
    }

    @Override
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // ClientId 检查
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }
        // 根据 option 分发
        String option = request.getOption();
        if ("create".equalsIgnoreCase(option)) {
            return createTask(userId, sessionId, request);
        } else if ("list".equalsIgnoreCase(option)) {
            return listTasks(userId);
        } else if ("delete".equalsIgnoreCase(option)) {
            return deleteTask(userId, request);
        } else {
            return Flux.just("无效的操作类型: " + option + ", 可选值: create, list, delete");
        }
    }

    /**
     * @description 创建定时任务
     * <p>插入 tbl_local_timer 表</p>
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

        // 检查任务是否已存在（按 title 查询）
        LambdaQueryWrapper<LocalTimer> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(LocalTimer::getUserId, userId);
        checkWrapper.eq(LocalTimer::getTitle, request.getTitle());
        LocalTimer existing = localTimerMapper.selectOne(checkWrapper);
        if (existing != null) {
            return Flux.just("创建失败: 任务标题 '" + request.getTitle() + "' 已存在");
        }

        // 构建 LocalTimer 实体
        LocalTimer timer = new LocalTimer();
        timer.setUserId(userId);
        timer.setSessionId(sessionId);
        timer.setTitle(request.getTitle());
        timer.setContent(request.getContent());
        timer.setStartTime(request.getStartTime());
        timer.setNextExecuteTime(startTimestamp);
        timer.setExecuteCount(request.getExecuteCount());
        timer.setIntervalSeconds(request.getIntervalSeconds());
        timer.setExecutedCount(0);
        timer.setStatus("ACTIVE");
        timer.setCreateTime(System.currentTimeMillis());

        // 写入 SQLite
        localTimerMapper.insert(timer);

        return Flux.just("定时任务创建成功\n- 标题: " + request.getTitle()
                + "\n- 启动时间: " + (StringUtils.isNotBlank(request.getStartTime()) ? request.getStartTime() : "立即")
                + "\n- 执行次数: " + (request.getExecuteCount() == -1 ? "无限循环" : String.valueOf(request.getExecuteCount()))
                + "\n- 执行间隔: " + request.getIntervalSeconds() + "秒");
    }

    /**
     * @description 查看定时任务列表
     * <p>查询 userId 所有定时任务返回列表</p>
     *
     * @author 陈晨
     */
    private Flux<String> listTasks(Long userId) {
        LambdaQueryWrapper<LocalTimer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LocalTimer::getUserId, userId);
        queryWrapper.orderByAsc(LocalTimer::getCreateTime);
        List<LocalTimer> timerList = localTimerMapper.selectList(queryWrapper);

        if (timerList.isEmpty()) {
            return Flux.just("暂无定时任务");
        }

        StringBuilder sb = new StringBuilder("定时任务列表:\n");
        int idx = 1;
        for (LocalTimer timer : timerList) {
            sb.append(idx++).append(". ").append(timer.getTitle())
                    .append(" [状态: ").append(timer.getStatus() != null ? timer.getStatus() : "UNKNOWN").append("]")
                    .append(" [已执行: ").append(timer.getExecutedCount() != null ? timer.getExecutedCount() : 0).append("次")
                    .append("/").append(timer.getExecuteCount() == null || timer.getExecuteCount() == -1 ? "无限" : String.valueOf(timer.getExecuteCount())).append("次]")
                    .append(" [下次执行: ").append(formatTime(timer.getNextExecuteTime())).append("]")
                    .append("\n");
        }
        return Flux.just(sb.toString());
    }

    /**
     * @description 删除定时任务
     * <p>按 title 删除</p>
     *
     * @author 陈晨
     */
    private Flux<String> deleteTask(Long userId, Request request) {
        if (StringUtils.isBlank(request.getTitle())) {
            return Flux.just("删除失败: title 不能为空");
        }
        // 按 userId + title 删除
        LambdaQueryWrapper<LocalTimer> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(LocalTimer::getUserId, userId);
        deleteWrapper.eq(LocalTimer::getTitle, request.getTitle());
        int deleted = localTimerMapper.delete(deleteWrapper);
        if (deleted == 0) {
            return Flux.just("删除失败: 任务 '" + request.getTitle() + "' 不存在");
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
     * <p>内部类，与原始 TimerTool 保持一致</p>
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

        @Description("操作类型: create-创建, list-查看列表, delete-删除")
        private String option;

        @Description("任务标题，唯一标识")
        private String title;

        @Description("待执行的任务内容")
        private String content;

        @Description("首次启动时间, 格式: yyyy-MM-dd HH:mm:ss")
        private String startTime;

        @Description("执行次数: -1无限循环, >=1执行N次")
        private Integer executeCount;

        @Description("执行间隔秒数")
        private Integer intervalSeconds;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }
}
