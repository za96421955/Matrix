package com.matrix.service.service.tool;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.RedisKey;
import com.matrix.service.cache.ServiceCache;
import com.matrix.service.service.agent.impl.TaskChainPatternService;
import com.matrix.service.service.tool.impl.TimerTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时任务调度器
 * <p>全局单例，每5秒扫描一次，检查并执行到期的定时任务。</p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class TimerTaskScheduler {

    @Resource
    private ServiceCache serviceCache;
    @Resource
    private TaskChainPatternService taskChainPatternService;

    /**
     * @description 定时扫描并执行任务
     * <p>每5秒执行一次，从Redis获取所有有定时任务的用户列表，遍历检查并执行到期的任务。</p>
     *
     * @author 陈晨
     */
    @Scheduled(fixedDelay = 5000)
    public void processTimerTasks() {
        log.debug("[定时任务调度] 轮询【开始】");
        try {
            // 1. 获取所有有定时任务的 userId
            Set<String> userIdSet = serviceCache.getSet().getAll(RedisKey.TIMER_USER_LIST.generateKey());
            if (userIdSet == null || userIdSet.isEmpty()) {
                return;
            }
            log.info("[定时任务调度] userIds={}", userIdSet);

            for (String userIdStr : userIdSet) {
                Long userId;
                try {
                    userId = Long.parseLong(userIdStr);
                } catch (NumberFormatException e) {
                    log.error("[定时任务调度] 用户ID格式异常: {}", userIdStr);
                    continue;
                }

                String taskKey = RedisKey.TIMER_USER_TASKS.generateKey(userId);

                // 2. 获取该用户的所有定时任务
                Map<String, String> entries = serviceCache.getHash().getAll(taskKey);
                if (entries == null || entries.isEmpty()) {
                    // 该用户没有任务了，从 Set 中移除
                    serviceCache.getSet().remove(RedisKey.TIMER_USER_LIST.generateKey(), userIdStr);
                    continue;
                }

                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    String title = entry.getKey();
                    String taskJson = entry.getValue();
                    TimerTool.TimerTaskInfo taskInfo;
                    try {
                        taskInfo = JSONObject.parseObject(taskJson, TimerTool.TimerTaskInfo.class);
                    } catch (Exception e) {
                        log.error("[定时任务调度] 解析任务信息失败, userId={}, title={}, error={}",
                                userId, title, e.getMessage());
                        continue;
                    }

                    if (taskInfo == null) {
                        continue;
                    }

                    // 3. 检查任务是否需要执行
                    if (!"ACTIVE".equals(taskInfo.getStatus())) {
                        continue;
                    }
                    long now = System.currentTimeMillis();
                    if (taskInfo.getNextExecuteTime() == null || taskInfo.getNextExecuteTime() > now) {
                        continue;
                    }

                    // 4. 尝试获取分布式锁（立即上锁，10分钟过期）
                    String lockKey = RedisKey.LOCK_KEY_PREFIX.generateKey(userId, title);
                    boolean lockAcquired = serviceCache.lock(lockKey, RedisKey.LOCK_KEY_PREFIX.getTtl());
                    if (!lockAcquired) {
                        // 锁已被其他实例持有，跳过
                        continue;
                    }

                    try {
                        // 5. 执行任务
                        log.info("[定时任务] 开始执行, userId={}, title={}, content={}",
                                userId, title, taskInfo.getContent());
                        PatternRequest patternRequest = PatternRequest.builder()
                                .userId(userId)
                                .sessionId(taskInfo.getSessionId())
                                .toolName(TimerTool.getName())
                                .messages(List.of(Message.user(taskInfo.getContent())))
                                .build();
                        taskChainPatternService.call(patternRequest)
                                .onErrorResume(e -> {
                                    log.error("[定时任务] userId={}, sessionId={}, task={}, 执行异常: {}",
                                            userId, taskInfo.getSessionId(), taskInfo.getTitle(), e.getMessage(), e);
                                    return Flux.just(Response.error(ErrorCode.CHAT_PROCESS_ERROR.getMessage() + ": " + e.getMessage()));
                                })
                                .blockFirst();

                        // 6. 更新任务信息（执行成功）
                        taskInfo.setExecutedCount(taskInfo.getExecutedCount() + 1);
                        taskInfo.setNextExecuteTime(now + taskInfo.getIntervalSeconds() * 1000L);

                        // 7. 检查是否已完成
                        if (taskInfo.getExecuteCount() > 0
                                && taskInfo.getExecutedCount() >= taskInfo.getExecuteCount()) {
                            taskInfo.setStatus("COMPLETED");
                            serviceCache.getHash().remove(taskKey, title);
                            log.info("[定时任务] 已完成, userId={}, title={}, totalExecuted={}",
                                    userId, title, taskInfo.getExecutedCount());
                        } else {
                            serviceCache.getHash().put(taskKey, title, JSONObject.toJSONString(taskInfo),
                                    RedisKey.TIMER_USER_TASKS.getTtl());
                            log.info("[定时任务] 执行完成, userId={}, title={}, executedCount={}",
                                    userId, title, taskInfo.getExecutedCount());
                        }
                    } catch (Exception e) {
                        log.error("[定时任务] 执行异常, userId={}, title={}, error={}",
                                userId, title, e.getMessage(), e);
                        // 失败计入执行次数
                        taskInfo.setExecutedCount(taskInfo.getExecutedCount() + 1);
                        taskInfo.setNextExecuteTime(now + taskInfo.getIntervalSeconds() * 1000L);
                        if (taskInfo.getExecuteCount() > 0
                                && taskInfo.getExecutedCount() >= taskInfo.getExecuteCount()) {
                            taskInfo.setStatus("COMPLETED");
                            serviceCache.getHash().remove(taskKey, title);
                        } else {
                            serviceCache.getHash().put(taskKey, title, JSONObject.toJSONString(taskInfo),
                                    RedisKey.TIMER_USER_TASKS.getTtl());
                        }
                    } finally {
                        // 8. 解锁
                        serviceCache.delete(lockKey);
                    }
                }

                // 9. 检查该用户是否还有任务，如果没任务了从 Set 移除
                Long size = serviceCache.getHash().size(taskKey);
                if (size == null || size == 0) {
                    serviceCache.getSet().remove(RedisKey.TIMER_USER_LIST.generateKey(), userIdStr);
                }
            }
        } catch (Exception e) {
            log.error("[定时任务调度] 全局异常: {}", e.getMessage(), e);
        } finally {
            log.debug("[定时任务调度] 轮询【结束】");
        }
    }

}


