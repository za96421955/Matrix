package com.matrix.common.dto.model;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 对话响应
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Response implements Serializable {
    @Serial
    private static final long serialVersionUID = 454214917503614103L;

    public interface Flag {
        String DATA = "data:";
        String DONE = "[DONE]";
    }

    private Long sessionId;
    
    private String id;
    private List<Choice> choices;
    private Long created;
    private String model;
    private String object;
    private Usage usage;
    private String system_fingerprint;
    private Error error;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    /**
     * @description 获取输出消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Message getMessage() {
        if (CollectionUtils.isEmpty(this.getChoices()) || null == this.getChoices().getFirst()) {
            return null;
        }
        // 同步: message
        if (null != this.getChoices().getFirst().getMessage()) {
            return this.getChoices().getFirst().getMessage();
        }
        // 流式: delta
        return this.getChoices().getFirst().getDelta();
    }

    /**
     * @description 获取思考
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String getReasoning() {
        Message message = this.getMessage();
        if (null == message) {
            return "";
        }
        return message.getReasoning_content();
    }

    /**
     * @description 获取回答
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String getAnswer() {
        Message message = this.getMessage();
        if (null == message) {
            return "";
        }
        return message.getContent();
    }

    /**
     * @description 获取工具调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public List<ToolCall> getToolCalls() {
        Message message = this.getMessage();
        if (null == message) {
            return Collections.emptyList();
        }
        return message.getTool_calls();
    }

    /**
     * @description 获取工具调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public ToolCall getToolCall() {
        List<ToolCall> toolCalls = this.getToolCalls();
        if (CollectionUtils.isEmpty(toolCalls)) {
            return null;
        }
        return toolCalls.getFirst();
    }

    /**
     * @description 转换 Response
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static Response parseResponse(String output) {
        Response response = null;
        try {
            if (output.indexOf(Response.Flag.DATA) == 0) {
                response = JSONObject.parseObject(output.substring(6), Response.class);
            } else {
                response = JSONObject.parseObject(output, Response.class);
            }
        } catch (Exception ignore) {}
        return response;
    }

    /**
     * @description 构建工具调用结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static Response tool(String toolCallId, String content) {
        return Response.builder()
                .choices(List.of(Choice.builder()
                        .message(Message.tool(toolCallId, content))
                        .build()))
                .build();
    }

    /**
     * @description 构建错误信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static Response error(String content) {
        return Response.builder()
                .error(Error.builder()
                        .message(content)
                        .build())
                .build();
    }

    /**
     * @description 构建错误信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static Response done() {
        return Response.builder()
                .choices(List.of(Choice.builder()
                        .delta(Message.builder()
                                .role(Role.DONE)
                                .build())
                        .build()))
                .build();
    }

    /**
     * @description 判断输出流是否已结束
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public boolean isDone() {
        if (null == this.getMessage()) {
            return false;
        }
        return Role.DONE.equalsIgnoreCase(this.getMessage().getRole());
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Choice implements Serializable {
        @Serial
        private static final long serialVersionUID = -3074179652661808940L;

        private String finish_reason;
        private Integer index;
        private Message message;      // 同步响应：message
        private Message delta;        // 流式响应：delta
        private Object logprobs;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolCall implements Serializable {
        @Serial
        private static final long serialVersionUID = -1413198549551690942L;

        private String id;
        private int index;
        private String type;
        private Function function;

        @Override
        /** 克隆对象副本 */
        public ToolCall clone() {
            return JSONObject.parseObject(JSONObject.toJSONString(this), ToolCall.class);
        }

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Function implements Serializable {
        @Serial
        private static final long serialVersionUID = -4881312085516648878L;

        private String name;
        private String arguments;

        /**
         * 流式增量拼接（专用于接收多个 delta 片段）
         * @param delta 新的 JSON 片段（直接追加）
         */
        public void appendArguments(String delta) {
            if (StringUtils.isBlank(delta)) {
                return;
            }
            if (StringUtils.isBlank(this.arguments)) {
                this.arguments = delta;
            } else {
                this.arguments += delta;
            }
        }

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Usage implements Serializable {
        @Serial
        private static final long serialVersionUID = 6472177721419359387L;

        private Integer completion_tokens;
        private Integer prompt_tokens;
        private Integer total_tokens;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Error implements Serializable {
        @Serial
        private static final long serialVersionUID = 7259467321952229557L;

        private String code;
        private String type;
        private String param;
        private String message;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}



