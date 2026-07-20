package com.matrix.service.service.tool;

import com.matrix.common.constant.ClientStatus;
import com.matrix.common.constant.Command;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.task.AuthService;
import com.matrix.service.service.task.Executor;
import com.matrix.service.service.user.ClientService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 工具抽象基类
 */
@Slf4j
@Component
public abstract class AbstractTool<R> implements Tool<R> {

    @Resource
    protected Executor executor;
    @Resource
    protected RegisterContext registerContext;
    @Resource
    protected AuthService authService;
    @Resource
    protected ClientService clientService;

    @Override
    public String systemPrompt(Long userId, Long sessionId, String clientId) {
        return "";
    }

    @Override
    public boolean isAnswer() {
        return false;
    }

    @Override
    public Flux<String> execute(Long userId, Long sessionId, String toolCallId, R request) {
        // 工具授权
        String reject = authService.commandAuth(userId, sessionId,
                Command.Type.TOOL, this.name(), "Tool: " + this.name() + "\n\n" + request.toString());
        if (StringUtils.isNotBlank(reject)) {
            return Flux.just(reject);
        }
        // 工具执行
        return executePass(userId, sessionId, toolCallId, request);
    }

    /**
     * @description 授权通过, 执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected abstract Flux<String> executePass(Long userId, Long sessionId, String toolCallId, R request);

    /**
     * @description 终端检查
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String checkClient(Long userId, String clientId) {
        if (StringUtils.isBlank(clientId)) {
            return "clientId 不可为空";
        }
        StringBuilder message = new StringBuilder();
        // ClientId 检查
        ClientInfo client = clientService.getById(userId, clientId);
        if (null == client) {
            message.append("clientId " + clientId + " 不存在");
        }
        if (!clientService.checkOnline(userId, clientId)) {
            message.append("clientId " + clientId + " 不在线");
        }
        if (StringUtils.isBlank(message.toString())) {
            return null;
        }
        message.append("\n---\n## 在线终端列表");
        for (ClientInfo clientInfo : clientService.getByUserId(userId)) {
            if (null == clientInfo || !ClientStatus.ONLINE.equalsIgnoreCase(clientInfo.getStatus())) {
                continue;
            }
            message.append(clientInfo.getPromptInfo());
        }
        return message.toString();
    }

}


