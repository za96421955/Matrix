package com.matrix.service.service.chat;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.ChatRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.service.context.ChatContext;
import com.matrix.service.dal.entity.MessageInfo;
import com.matrix.service.service.agent.PatternService;
import com.matrix.service.service.agent.impl.DefaultPatternService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat 服务实现类
 * 负责对话流程编排、SSE 流式响应、异常回退机制
 */
@Slf4j
@Service
public class ChatService {

    @Resource
    private ChatContext chatContext;
    @Resource
    private SessionService sessionService;
    @Resource
    private MessageService messageService;
    @Resource
    private DefaultPatternService defaultPatternService;

    /**
     * @description 对话入口（SSE 流式）
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Flux<Response> completions(ChatRequest request) {
        if (chatContext.isConversation(request.getUserId(), request.getSessionId())) {
            return Flux.just(Response.error(ErrorCode.IN_THE_CONVERSATION.getMessage()));
        }
        // 获取模式服务
        PatternService patternService = defaultPatternService.getPatternService(request.getPattern());
        // 流式响应
        return Mono.fromCallable(() -> sessionService.getOrCreateSession(request, request.getMessages().getFirst().getContent()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(session -> {
                    // 标记: 对话中
                    chatContext.inConversation(request.getUserId(), session.getId());

                    // 设置 agent 请求参数
                    request.setSessionId(session.getId());
                    request.setMessages(this.buildMessages(
                            request.getUserId(),
                            request.getSessionId(),
                            request.getMessages(),
                            request.getReferencedSessionIds()));
                    if (StringUtils.isNotBlank(session.getAgent())) {
                        request.setAgent(session.getAgent());
                    }
                    if (null != session.getAuthLevel()) {
                        request.setAuthLevel(session.getAuthLevel());
                    }
                    // 关闭深度思考，则清空思考深度
                    if (!request.getThinking().isEnabled()) {
                        request.setReasoning_effort(null);
                    }

                    // 2. 构建共享的 agent 流
                    // 将冷流变为热流，多播给多个订阅者
                    Flux<Response> sharedAgentFlux = patternService.call(request).share();

                    // 3. 独立的后台消费者：负责处理业务逻辑（完全不受客户端断开影响）
                    sharedAgentFlux.subscribeOn(Schedulers.boundedElastic())  // 在弹性线程池中执行，避免阻塞
                            .onErrorResume(e -> {
                                log.error("Chat completions error for sessionId={}: {}", request.getSessionId(), e.getMessage(), e);
                                return Flux.just(Response.error(ErrorCode.CHAT_PROCESS_ERROR.getMessage() + ": " + e.getMessage()));
                            })
                            .doFinally(signalType -> {
                                // 无论正常完成、错误还是取消，这里都可以做最终的收尾工作
                                try {
                                    chatContext.stopConversation(request.getUserId(), request.getSessionId());
                                } catch (Exception e) {
                                    // 异常, 再次尝试
                                    try {
                                        Thread.sleep(3000);
                                        chatContext.stopConversation(request.getUserId(), request.getSessionId());
                                    } catch (Exception ignore) {}
                                }
                            })
                            .subscribe();   // 触发后台订阅

                    // 4. 返回给客户端的流：只做轻量转换，不包含重业务逻辑
                    return sharedAgentFlux
                            .map(response -> {
                                response.setSessionId(request.getSessionId());
                                return response;
                            })
                            .onErrorResume(e -> {
                                log.error("Chat completions error for sessionId={}: {}", request.getSessionId(), e.getMessage(), e);
                                return Flux.just(Response.error(ErrorCode.CHAT_PROCESS_ERROR.getMessage() + ": " + e.getMessage()));
                            });
                })
                // 结束对话
                .doFinally(signalType -> {
                    // 留空，或者仅处理最外层的资源释放
                    // 业务最终的清理已经在后台消费者的 doFinally 中做了
                    log.warn("[Chat 终止] 对话响应终止！！！");
                });
    }

    /**
     * @description 构建消息集合
     * <p>
     * 消息构建顺序：
     * 1. 引用会话的全量消息（按 referencedSessionIds 顺序依次加载）
     * 2. 当前会话的历史消息
     * 3. 当前用户新输入的消息
     * 引用会话的消息不会持久化到当前会话的 message 表中
     * </p>
     *
     * @author 陈晨
     */
    private List<Message> buildMessages(long userId, long sessionId, List<Message> messages, List<Long> referencedSessionIds) {
        List<Message> buildMessages = new ArrayList<>();

        // 1. 加载引用会话的消息（作为上下文前置，不持久化）
        if (!CollectionUtils.isEmpty(referencedSessionIds)) {
            for (Long refSessionId : referencedSessionIds) {
                if (refSessionId == null || refSessionId.equals(sessionId)) {
                    continue; // 跳过空引用和自身引用
                }
                try {
                    List<MessageInfo> refMessageList = messageService.getChatList(userId, refSessionId);
                    for (MessageInfo info : refMessageList) {
                        Message msg = messageService.convert(info);
                        if (msg != null && CollectionUtils.isEmpty(msg.getTool_calls())) {
                            buildMessages.add(msg);
                        }
                    }
                    log.info("userId={}, referenced sessionId={}, loaded {} messages as context",
                            userId, refSessionId, refMessageList.size());
                } catch (Exception e) {
                    log.error("userId={}, referenced sessionId={}, 加载引用会话消息异常: {}",
                            userId, refSessionId, e.getMessage(), e);
                }
            }
        }

        // 2. 查询当前会话的历史消息
        List<MessageInfo> messageInfoList = messageService.getChatList(userId, sessionId);
        for (MessageInfo message : messageInfoList) {
            Message converted = messageService.convert(message);
            if (converted == null) {
                continue;
            }
            if (CollectionUtils.isEmpty(converted.getTool_calls())) {
                buildMessages.add(converted);
            }
        }

        // 3. 合并用户新输入的消息并入库
        try {
            for (Message message : messages) {
                buildMessages.add(message);
                // 入库
                MessageInfo messageInfo = messageService.convert(message);
                messageInfo.setUserId(userId);
                messageInfo.setSessionId(sessionId);
                messageService.save(messageInfo);
                log.info("userId={}, sessionId={}, message={}, 用户 input 消息入库",
                        userId, sessionId, JSON.toJSONString(message));
            }
            return buildMessages;
        } catch (Exception e) {
            log.error("userId={}, sessionId={}, messages={}, 记录 user 消息异常: {}",
                    userId, sessionId, messages, e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }
    }

}
