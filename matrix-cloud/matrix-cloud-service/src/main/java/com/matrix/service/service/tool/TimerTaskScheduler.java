package com.matrix.service.service.tool;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.RedisKey;
import com.matrix.service.service.agent.impl.TaskPatternService;
import com.matrix.service.service.tool.impl.TimerTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private TaskPatternService taskPatternService;

    /**
     * @description 定时扫描并执行任务
     * <p>每5秒执行一次，从Redis获取所有有定时任务的用户列表，遍历检查并执行到期的任务。</p>
     *
     * @author 陈晨
     */
    @Scheduled(fixedDelay = 5000)
    public void processTimerTasks() {
//        log.info("[定时任务调度] 轮询【开始】");
        try {
            // 1. 获取所有有定时任务的 userId
            Set<Object> userIdSet = redisTemplate.opsForSet().members(RedisKey.TIMER_USER_LIST.generateKey());
            if (userIdSet == null || userIdSet.isEmpty()) {
                return;
            }
            log.info("[定时任务调度] userIds={}", userIdSet);

            for (Object userIdObj : userIdSet) {
                String userIdStr = (String) userIdObj;
                Long userId;
                try {
                    userId = Long.parseLong(userIdStr);
                } catch (NumberFormatException e) {
                    log.error("[定时任务调度] 用户ID格式异常: {}", userIdStr);
                    continue;
                }

                String taskKey = RedisKey.TIMER_USER_TASKS.generateKey(userId);

                // 2. 获取该用户的所有定时任务
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(taskKey);
                if (entries == null || entries.isEmpty()) {
                    // 该用户没有任务了，从 Set 中移除
                    redisTemplate.opsForSet().remove(RedisKey.TIMER_USER_LIST.generateKey(), userIdStr);
                    continue;
                }

                for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                    String title = (String) entry.getKey();
                    String taskJson = (String) entry.getValue();
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
                    Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                            RedisKey.LOCK_KEY_PREFIX.getTtl(), TimeUnit.SECONDS);
                    if (Boolean.FALSE.equals(lockAcquired)) {
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
                        taskPatternService.call(patternRequest)
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
                            redisTemplate.opsForHash().delete(taskKey, title);
                            log.info("[定时任务] 已完成, userId={}, title={}, totalExecuted={}",
                                    userId, title, taskInfo.getExecutedCount());
                        } else {
                            redisTemplate.opsForHash().put(taskKey, title, JSONObject.toJSONString(taskInfo));
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
                            redisTemplate.opsForHash().delete(taskKey, title);
                        } else {
                            redisTemplate.opsForHash().put(taskKey, title, JSONObject.toJSONString(taskInfo));
                        }
                    } finally {
                        // 8. 解锁
                        redisTemplate.delete(lockKey);
                    }
                }

                // 9. 检查该用户是否还有任务，如果没任务了从 Set 移除
                Long size = redisTemplate.opsForHash().size(taskKey);
                if (size == null || size == 0) {
                    redisTemplate.opsForSet().remove(RedisKey.TIMER_USER_LIST.generateKey(), userIdStr);
                }
            }
        } catch (Exception e) {
            log.error("[定时任务调度] 全局异常: {}", e.getMessage(), e);
        } finally {
//            log.info("[定时任务调度] 轮询【结束】");
        }
    }

}
