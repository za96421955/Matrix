//package com.matrix.service.service.agent.impl;
//
//import com.matrix.common.constant.Constant;
//import com.matrix.common.dto.model.Message;
//import com.matrix.common.dto.model.Response;
//import com.matrix.common.dto.request.PatternRequest;
//import com.matrix.common.enums.CodingPattern;
//import com.matrix.common.enums.ErrorCode;
//import com.matrix.service.context.CodingPatternContext;
//import com.matrix.service.dal.entity.ClientInfo;
//import com.matrix.service.service.agent.AbstractPatternService;
//import com.matrix.service.service.agent.Prompt;
//import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.stereotype.Service;
//import org.springframework.util.CollectionUtils;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.FluxSink;
//
//import java.util.List;
//
///**
// * @description 编程模式
// * <p> <功能详细描述> </p>
// *
// * @author 陈晨
// */
//@Slf4j
//@Service
//public class CodingPatternService extends AbstractPatternService<PatternRequest> {
//
//    @Resource
//    private CodingPatternContext codingPatternContext;
//    @Resource
//    private TaskChainPatternService taskChainPatternService;
//
//    @Override
//    public Flux<Response> call(PatternRequest request) {
//        if (request == null || StringUtils.isBlank(request.getItemPath())) {
//            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
//        }
//        // 终端
//        List<ClientInfo> clients = clientService.getByUserIdAndOnline(request.getUserId());
//        if (CollectionUtils.isEmpty(clients)) {
//            return Flux.just(Response.error(ErrorCode.CLIENT_NOT_FOUND.getMessage()));
//        }
//        // 工具
//        request.setTools(this.buildTools());
//        // 消息
//        request.setMessages(this.buildMessages(request, clients, null));
//        // ReAct Agent Call
//        return this.call(request, sink -> {
//            log.info("[编程模式] userId={}, 执行【开始】", request.getUserId());
//            this.executor(sink, request);
//            log.info("[编程模式] userId={}, 执行【结束】", request.getUserId());
//        });
//    }
//
//    /**
//     * @description 执行
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    public void executor(FluxSink<Response> sink, PatternRequest request) {
//        if (null == sink || null == request) {
//            return;
//        }
//        // 获取环节
//        int no = codingPatternContext.getPatternNo(request.getUserId(), request.getSessionId());
//        // agent call
//        String result = this.callNoToolByClone(sink, request, Prompt.Common.GET_PATTERN_NO.formatted(
//                request.getItemPath(), CodingPattern.getPrompt(), no));
//        try {
//            no = Integer.parseInt(result);
//        } catch (Exception e) {
//            no = CodingPattern.DEMAND_ANALYZE.getNo();
//            log.info("[编程模式] userId={}, no={}, 新任务/环节信息不存在, 重新设置环节",
//                    request.getUserId(), no);
//        }
//        // 设置环节
//        codingPatternContext.setPatternNo(request.getUserId(), request.getSessionId(), no);
//        log.info("[编程模式] userId={}, no={}, 设置环节", request.getUserId(), no);
//
//        // 环节处理
//        while ((no = codingPatternContext.getPatternNo(request.getUserId(), request.getSessionId())) > 0) {
//            // P2.1. 需求分析
//            if (CodingPattern.DEMAND_ANALYZE.eq(no)) {
//                // agent call
//                result = this.callResultByClone(sink, request, Prompt.Common.DEMAND_ANALYZE.formatted(
//                        request.getItemPath(), Constant.PASS));
//                request.getMessages().add(Message.assistant(result));
//                log.info("[编程模式] userId={}, result={}, 需求分析", request.getUserId(), result);
//                // 下一环节
//                if (result.contains(Constant.PASS)) {
//                    codingPatternContext.next(request.getUserId(), request.getSessionId(), CodingPattern.PLAN_DEVELOP);
//                    continue;
//                } else {
//                    break;
//                }
//            }
//
//            // P3.1. 开发任务规划（plan）
//            if (CodingPattern.PLAN_DEVELOP.eq(no)) {
//                // agent call
//                result = this.callResultByClone(sink, request, Prompt.Coding.PLAN_DEVELOP.formatted(
//                        request.getItemPath(), Constant.PASS));
//                request.getMessages().add(Message.assistant(result));
//                log.info("[编程模式] userId={}, result={}, 开发任务规划", request.getUserId(), result);
//                // 下一环节
//                if (result.contains(Constant.PASS)) {
//                    codingPatternContext.next(request.getUserId(), request.getSessionId(), CodingPattern.DEVELOP_EXECUTOR);
//                    continue;
//                } else {
//                    break;
//                }
//            }
//            // TODO P3.2. 测试任务规划（plan）
//            // TODO P3.3. 部署任务规划（plan）
//            // TODO P3.4. 其他任务规划（plan）
//
//            // P4.1. 开发任务执行
//            if (CodingPattern.DEVELOP_EXECUTOR.eq(no)) {
//                // agent call
//                String prompt = Prompt.Common.WORKING_DIRECTORY.formatted(request.getItemPath()) + "执行<研发>任务";
//                request.getMessages().add(Message.user(prompt));
//                result = taskChainPatternService.executor(sink, request);
//                log.info("[编程模式] userId={}, result={}, 开发任务执行", request.getUserId(), result);
//                // 任务结束, 清理缓存
//                codingPatternContext.clear(request.getUserId(), request.getSessionId());
//                break;
//            }
//
//            // TODO P5.1. 测试任务执行（goto P4.1）
//            // TODO P6.1. 部署任务执行
//            // TODO P6.2. 部署结果检查（goto P6.1）
//            // TODO P7.1. 其他任务执行
//
//            // 没有经过任何处理, 退出
//            log.warn("[编程模式] userId={}, no={}, 无法处理的环节【退出】", request.getUserId(), no);
//            break;
//        }
//    }
//
//}
//
//
