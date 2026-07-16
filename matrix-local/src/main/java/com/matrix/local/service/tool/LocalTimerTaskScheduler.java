package com.matrix.local.service.tool;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.local.dal.entity.LocalTimer;
import com.matrix.local.dal.mapper.LocalTimerMapper;
import com.matrix.service.service.agent.impl.TaskPatternService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地定时任务调度器
 * <p>每5秒扫描 tbl_local_timer 表，检查并执行到期的定时任务。
 * 使用 ConcurrentHashMap 本地锁替代 Redis 分布式锁。</p>
 *
 * @author 陈晨
 */
@Slf4j
@Primary
@Component
public class LocalTimerTaskScheduler {

    /**
     * 本地任务锁，key = userId + ":" + title，防止同一任务在调度周期内重复执行
     */
    private final ConcurrentHashMap<String, Boolean> localLocks = new ConcurrentHashMap<>();

    @Resource
    private LocalTimerMapper localTimerMapper;

    @Resource
    private TaskPatternService taskPatternService;

    /**
     * @description 定时扫描并执行任务
     * <p>每5秒执行一次，从 tbl_local_timer 表查询所有状态为 ACTIVE 且 next_execute_time 小于等于当前时间的任务，
     * 使用 ConcurrentHashMap 本地锁防止重复执行。</p>
     */
    @Scheduled(fixedDelay = 5000)
    public void processTimerTasks() {
        try {
            // 1. 查询所有到期的 ACTIVE 任务
            long now = System.currentTimeMillis();
            LambdaQueryWrapper<LocalTimer> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(LocalTimer::getStatus, "ACTIVE");
            queryWrapper.le(LocalTimer::getNextExecuteTime, now);
            // 按 next_execute_time 升序排列，优先执行最早到期的任务
            queryWrapper.orderByAsc(LocalTimer::getNextExecuteTime);
            List<LocalTimer> timerList = localTimerMapper.selectList(queryWrapper);

            if (timerList == null || timerList.isEmpty()) {
                return;
            }

            log.info("[本地定时任务调度] 到期任务数: {}", timerList.size());

            for (LocalTimer timer : timerList) {
                if (timer == null || timer.getUserId() == null || timer.getTitle() == null) {
                    continue;
                }

                String lockKey = timer.getUserId() + ":" + timer.getTitle();

                // 2. 尝试获取本地锁（putIfAbsent 原子操作）
                Boolean existing = localLocks.putIfAbsent(lockKey, Boolean.TRUE);
                if (existing != null) {
                    // 锁已被持有，跳过（同一 JVM 内不重复执行）
                    continue;
                }

                try {
                    // 3. 执行任务（释放锁前先移除，避免死锁）
                    log.info("[本地定时任务] 开始执行, userId={}, title={}, content={}",
                            timer.getUserId(), timer.getTitle(), timer.getContent());

                    PatternRequest patternRequest = PatternRequest.builder()
                            .userId(timer.getUserId())
                            .sessionId(timer.getSessionId())
                            .toolName(LocalTimerTool.getName())
                            .messages(List.of(Message.user(timer.getContent())))
                            .build();

                    taskPatternService.call(patternRequest)
                            .onErrorResume(e -> {
                                log.error("[本地定时任务] userId={}, sessionId={}, task={}, 执行异常: {}",
                                        timer.getUserId(), timer.getSessionId(), timer.getTitle(), e.getMessage(), e);
                                return Flux.just(Response.error(ErrorCode.CHAT_PROCESS_ERROR.getMessage() + ": " + e.getMessage()));
                            })
                            .blockFirst();

                    // 4. 更新任务信息（执行成功）
                    int executedCount = (timer.getExecutedCount() != null ? timer.getExecutedCount() : 0) + 1;
                    long nextExecuteTime = now + (timer.getIntervalSeconds() != null ? timer.getIntervalSeconds() : 0) * 1000L;

                    // 5. 检查是否已完成
                    LambdaUpdateWrapper<LocalTimer> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(LocalTimer::getId, timer.getId());
                    updateWrapper.set(LocalTimer::getExecutedCount, executedCount);

                    boolean isCompleted = false;
                    if (timer.getExecuteCount() != null && timer.getExecuteCount() > 0
                            && executedCount >= timer.getExecuteCount()) {
                        // 已完成，更新状态为 COMPLETED
                        updateWrapper.set(LocalTimer::getStatus, "COMPLETED");
                        isCompleted = true;
                        log.info("[本地定时任务] 已完成, userId={}, title={}, totalExecuted={}",
                                timer.getUserId(), timer.getTitle(), executedCount);
                    } else {
                        // 未完成，更新 next_execute_time
                        updateWrapper.set(LocalTimer::getNextExecuteTime, nextExecuteTime);
                        log.info("[本地定时任务] 执行完成, userId={}, title={}, executedCount={}",
                                timer.getUserId(), timer.getTitle(), executedCount);
                    }

                    localTimerMapper.update(null, updateWrapper);

                } catch (Exception e) {
                    log.error("[本地定时任务] 执行异常, userId={}, title={}, error={}",
                            timer.getUserId(), timer.getTitle(), e.getMessage(), e);

                    // 失败计入执行次数
                    int executedCount = (timer.getExecutedCount() != null ? timer.getExecutedCount() : 0) + 1;
                    long nextExecuteTime = now + (timer.getIntervalSeconds() != null ? timer.getIntervalSeconds() : 0) * 1000L;

                    LambdaUpdateWrapper<LocalTimer> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(LocalTimer::getId, timer.getId());
                    updateWrapper.set(LocalTimer::getExecutedCount, executedCount);

                    if (timer.getExecuteCount() != null && timer.getExecuteCount() > 0
                            && executedCount >= timer.getExecuteCount()) {
                        updateWrapper.set(LocalTimer::getStatus, "COMPLETED");
                    } else {
                        updateWrapper.set(LocalTimer::getNextExecuteTime, nextExecuteTime);
                    }
                    localTimerMapper.update(null, updateWrapper);

                } finally {
                    // 6. 释放本地锁
                    localLocks.remove(lockKey);
                }
            }
        } catch (Exception e) {
            log.error("[本地定时任务调度] 全局异常: {}", e.getMessage(), e);
        }
    }

}
