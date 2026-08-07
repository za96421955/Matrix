package com.matrix.service.service.agent.impl;

import com.matrix.common.constant.Constant;
import com.matrix.common.constant.OutputKeyword;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.TaskMode;
import com.matrix.common.util.ContentUtil;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.common.util.JSONUtil;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.agent.schema.Smart;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * @description 深度模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class DeepPatternService extends AbstractPatternService<PatternRequest> {

    @Override
    public Flux<Response> call(PatternRequest request) {
        if (request == null) {
            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
        }
        // 重置上下文
        this.resetContext(request);
        // 终端
        List<ClientInfo> clients = clientService.getByUserIdAndOnline(request.getUserId());
        // 工具
        request.setTools(this.buildTools());
        // 消息
        request.setMessages(this.buildMessages(request, clients, null));
        // ReAct Agent Call
        return this.call(request, sink -> {
            log.info("[深度模式] userId={}, 执行【开始】", request.getUserId());
            this.executor(sink, request);
            log.info("[深度模式] userId={}, 执行【结束】", request.getUserId());
        });
    }

    /**
     * @description 执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void executor(FluxSink<Response> sink, PatternRequest request) {
        if (null == sink || null == request) {
            return;
        }
        // 记录模式
        patternContext.setPattern(request.getUserId(), request.getSessionId(), Constant.Pattern.DEEP);

        // TODO 1. 违规初筛

        // TODO 2. 确定目标 (hook)

        // TODO 3. 确定安全边界 (hook)

        // TODO 4. 制定信息收集方案 (hook)

        // TODO 5. 执行收集信息

        // TODO 6. 信息事实核查

        // TODO 7. 前瞻性决策

        // TODO 8. 制定执行计划 (hook)

        // TODO 9. 安全围栏检查

        // TODO 10. 递进生成执行方案 (多大深度 3)
        // TODO 10.1. 执行方案单步/多步检查
        // TODO 10.2. 生成执行方案 -> 10.1
        // TODO 10.3. 输出执行方案列表

        // TODO 11. 循环执行方案列表
        // TODO 11.1. 方案执行 (hook)
        // TODO 11.2. 结果审查 -> PASS/REVISE
        // TODO 11.3. 修订建议合并至下一次执行 -> 11.1.

        // TODO 12. 最后一次结果修订 (如果有)

        // 清除模式缓存
        patternContext.clear(request.getUserId(), request.getSessionId());
    }

}


