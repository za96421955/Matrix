package com.matrix.service.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.dal.mapper.ClientInfoMapper;
import com.matrix.service.service.agent.ModelService;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.service.task.Executor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆刷新定时器
 * <p> 每天0点自动维护用户各在线终端的当前记忆 </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@EnableScheduling
public class MemoryRefreshScheduler {

    @Resource
    private ClientInfoMapper clientInfoMapper;

    @Resource
    private ClientService clientService;

    @Resource
    private Executor executor;

    @Resource
    private ModelService modelService;

    @Resource
    private RegisterContext registerContext;

    /**
     * 每日0点执行记忆刷新
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshMemoryDaily() {
        log.info("[记忆刷新] 开始执行每日记忆刷新任务");
        try {
            // 1. 获取所有有终端的用户ID（去重）
            List<Object> userIdObjs = clientInfoMapper.selectObjs(
                    new QueryWrapper<ClientInfo>()
                            .select("distinct user_id")
                            .isNotNull("user_id")
            );
            List<Long> userIds = userIdObjs.stream()
                    .map(obj -> Long.valueOf(obj.toString()))
                    .distinct()
                    .toList();

            if (userIds.isEmpty()) {
                log.info("[记忆刷新] 没有需要刷新的用户");
                return;
            }
            log.info("[记忆刷新] 共发现 {} 个用户需要处理", userIds.size());

            // 2. 遍历每个用户
            for (Long userId : userIds) {
                try {
                    // 2a. 获取在线终端
                    List<ClientInfo> onlineClients = clientService.getByUserIdAndOnline(userId);
                    if (onlineClients == null || onlineClients.isEmpty()) {
                        log.debug("[记忆刷新] userId={}, 没有在线终端，跳过", userId);
                        continue;
                    }

                    // 2b. 获取用户模型配置
                    RegisterCommand.Model model = registerContext.getModel(userId, Constant.Model.DEEPSEEK_V4_FLASH);
                    if (model == null) {
                        log.warn("[记忆刷新] userId={}, 未配置模型，跳过", userId);
                        continue;
                    }

                    // 3. 遍历每个在线终端
                    for (ClientInfo client : onlineClients) {
                        String clientId = client.getClientId();
                        try {
                            log.debug("[记忆刷新] 开始刷新 userId={}, clientId={}", userId, clientId);

                            // 3a. 读取当前记忆
                            String memory = executor.executeCommand(clientId, Constant.SYSTEM_COMMAND.READ_MEMORY).block();
                            if (memory == null || memory.isBlank()) {
                                log.debug("[记忆刷新] userId={}, clientId={}, 记忆为空，跳过", userId, clientId);
                                continue;
                            }

                            // 3b. 调用AI刷新记忆
                            String refreshPrompt = "请整理、刷新当前记忆，将过期的短期记忆归档或删除，"
                                    + "将重要的信息沉淀到长期记忆，整理谏言，仅输出合并后的记忆内容，不要其他说明\n\n"
                                    + "## 当前记忆\n```\n" + memory + "\n```";
                            String newMemory = modelService.call(model, refreshPrompt);
                            if (newMemory == null || newMemory.isBlank()) {
                                log.warn("[记忆刷新] userId={}, clientId={}, AI返回为空，跳过", userId, clientId);
                                continue;
                            }

                            // 3c. 写回刷新后的记忆
                            executor.executeCommand(clientId, Constant.SYSTEM_COMMAND.WRITE_MEMORY + newMemory).block();
                            log.info("[记忆刷新] userId={}, clientId={}, 刷新成功", userId, clientId);
                        } catch (Exception e) {
                            log.error("[记忆刷新] userId={}, clientId={}, 刷新异常: {}",
                                    userId, clientId, e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("[记忆刷新] userId={}, 处理异常: {}", userId, e.getMessage(), e);
                }
            }
            log.info("[记忆刷新] 每日记忆刷新任务执行完成");
        } catch (Exception e) {
            log.error("[记忆刷新] 执行异常: {}", e.getMessage(), e);
        }
    }

}
