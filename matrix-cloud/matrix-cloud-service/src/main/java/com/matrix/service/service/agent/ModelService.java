package com.matrix.service.service.agent;

import com.matrix.common.constant.Constant;
import com.matrix.common.constant.SystemParam;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Request;
import com.matrix.common.dto.model.Response;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.util.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

/**
 * 模型服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class ModelService {

    /**
     * @description API Key 检查
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public boolean checkApiKey(String apiKey) {
        RegisterCommand.Model model = RegisterCommand.Model.builder()
                .baseUrl(Constant.Model.BASE_URL)
                .model(Constant.Model.DEEPSEEK_V4_FLASH)
                .apiKey(apiKey)
                .build();
        Request request = Request.builder()
                .model(model.getModel())
                .messages(List.of(Message.user("直接返回: OK")))
                .max_tokens(SystemParam.MAX_TOKENS)
                .thinking(Request.Thinking.disabled())
                .stream(false)
                .build();
        Response response = this.call(model, request);
        if (null != response && null != response.getError()) {
            log.warn("[APIKey 检查] apiKey={} 检查不通过: {}", apiKey, response.getError());
            return false;
        }
        return true;
    }

    /**
     * @description 同步调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Response call(RegisterCommand.Model model, Request request) {
        return this.call(model, request, 0);
    }

    public Response call(RegisterCommand.Model model, Request request, int retry) {
        String error;
        try {
            log.debug("[模型同步请求] model={}, request={}, retry={}",
                    model.getModel(), request, retry);
            return this.callByThrowException(model, request);
        } catch (IOException e) {
            log.error("[模型同步请求] model={}, request={}, retry={}, IO异常, 重置连接池: {}",
                    model.getModel(), request, retry, e.getMessage(), e);
            HttpClient.reset();
            error = e.getMessage();
        } catch (Exception e) {
            log.error("[模型同步请求] model={}, request={}, retry={}, 异常: {}",
                    model.getModel(), request, retry, e.getMessage(), e);
            error = e.getMessage();
        }
        if (retry >= 5) {
            return Response.error(error);
        }
        try {
            Thread.sleep(1000L * retry * retry);
        } catch (Exception ignore) {}
        return this.call(model, request, ++retry);
    }

    private Response callByThrowException(RegisterCommand.Model model, Request request) throws IOException {
        if (null == model || null == request || CollectionUtils.isEmpty(request.getMessages())) {
            throw new RuntimeException(ErrorCode.MODEL_PARAM_INVALID.getMessage());
        }

        // 构建请求参数
        log.debug("[模型同步请求] model={}, request={}", model.getModel(), request);
        request.setModel(model.getModel());
        String output = HttpClient.post(model.getBaseUrl() + Constant.Model.COMPLETIONS)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .authorization(model.getApiKey())
                .body(request.toString())
                .asString();
        log.debug("[模型同步请求] model={}, output={}", model.getModel(), output);
        if (StringUtils.isBlank(output)) {
            return null;
        }
        Response response = Response.parseResponse(output);
        // 错误
        Response.Error error = response.getError();
        if (null != error) {
            log.error("[模型同步请求] model={}, request={}, 失败: {}",
                    model.getModel(), request, error.getMessage());
            throw new RuntimeException(error.getMessage());
        }
        // response is empty
        if (StringUtils.isBlank(response.getReasoning())
                && StringUtils.isBlank(response.getAnswer())
                && CollectionUtils.isEmpty(response.getToolCalls())) {
            log.error("[模型同步请求] model={}, request={}, 失败: 响应全部为空",
                    model.getModel(), request);
            throw new RuntimeException(ErrorCode.MODEL_MESSAGE_EMPTY.getMessage());
        }
        // 若 answer 为空, reasoning 不为空, 则 answer = reasoning
        if (StringUtils.isNotBlank(response.getReasoning())
                && StringUtils.isBlank(response.getAnswer())) {
            log.warn("[模型同步请求] model={}, request={}, answer 为空, reasoning 不为空, answer = reasoning",
                    model.getModel(), request);
            response.getMessage().setContent(response.getReasoning());
        }
        return response;
    }

    /**
     * @description 同步调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String call(RegisterCommand.Model model, String message) {
        return this.call(model, Request.build(model.getModel(), List.of(Message.user(message)))).getAnswer();
    }

    /**
     * @description 流式调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void stream(RegisterCommand.Model model, Request request, HttpClient.StreamHandle handle) {
        if (null == model || null == request || CollectionUtils.isEmpty(request.getMessages())) {
            return;
        }
        log.debug("[模型流式请求] model={}, request={}", model.getModel(), request);
        try {
            request.setModel(model.getModel());
            HttpClient.post(model.getBaseUrl() + Constant.Model.COMPLETIONS)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .authorization(model.getApiKey())
                    .body(request.toString())
                    .asStream(handle);
        } catch (Exception e) {
            log.error("[模型流式请求] model={}, requst={}, 异常={}",
                    model.getModel(), request, e.getMessage(), e);
        }
    }

    /**
     * @description 流式调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void stream(RegisterCommand.Model model, String message, HttpClient.StreamHandle handle) {
        this.stream(model, Request.build(model.getModel(), List.of(Message.user(message))).stream(), handle);
    }

//    /**
//     * @description 获取回答
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    public String callAnswer(List<Message> messages) {
//        if (CollectionUtils.isEmpty(messages)) {
//            throw new RuntimeException(ErrorCode.SYSTEM_ERROR.getMessage());
//        }
//        RegisterCommand.Model model = RegisterCommand.Model.builder()
//                .baseUrl(Constant.Model.BASE_URL)
//                .model(Constant.Model.DEEPSEEK_V4_FLASH)
//                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
//                .build();
//        Request request = Request.builder()
//                .model(model.getModel())
//                .messages(messages)
//                .max_tokens(SystemParam.MAX_TOKENS)
//                .thinking(Request.Thinking.enabled())
//                .reasoning_effort("low")
//                .stream(false)
//                .build();
//        Response response = this.call(model, request);
//        if (null == response) {
//            throw new RuntimeException(ErrorCode.SYSTEM_ERROR.getMessage());
//        }
//        if (null != response.getError()) {
//            throw new RuntimeException(response.getError().getMessage());
//        }
//        return response.getMessage().getContent();
//    }
//
//    public String callAnswer(List<Message> messages, String input) {
//        if (CollectionUtils.isEmpty(messages)) {
//            throw new RuntimeException(ErrorCode.SYSTEM_ERROR.getMessage());
//        }
//        log.info("[直接回答] prompt={}", input);
//        List<Message> localMessages = new ArrayList<>(messages);
//        localMessages.add(Message.user(input));
//        return this.callAnswer(localMessages);
//    }

}


