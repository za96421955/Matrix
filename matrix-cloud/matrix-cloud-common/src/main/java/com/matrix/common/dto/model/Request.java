package com.matrix.common.dto.model;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.common.util.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 对话请求
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Request implements Serializable {
    @Serial
    private static final long serialVersionUID = -5971976031256584323L;

    private String model;
    private List<Message> messages;
    private List<Tool> tools;
    /** 生成 completion 的最大 token 数 */
    private Integer max_tokens;
    /** 深度思考 */
    private Thinking thinking;
    /** 思考深度: high, max */
    private String reasoning_effort;
    /** 流式输出 */
    private Boolean stream;

    /** 构建对象或命令 */
    public static Request build(String model, List<Message> messages, List<Tool> tools) {
        return Request.builder()
                .model(model)
                .messages(messages)
                .tools(tools)
                .max_tokens(4096)
                .thinking(Thinking.enabled())
                .reasoning_effort("high")
                .stream(false)
                .build();
    }

    /** 构建对象或命令 */
    public static Request build(String model, List<Message> messages) {
        return build(model, messages, null);
    }

    /**
     * @description 开启深度思考
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Request enabledThinking() {
        this.thinking = Thinking.enabled();
        this.reasoning_effort = "high";
        return this;
    }

    /**
     * @description 关闭深度思考
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Request disabledThinking() {
        this.thinking = Thinking.disabled();
        this.reasoning_effort = null;
        return this;
    }

    /**
     * @description 流式输出
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Request stream() {
        this.stream = true;
        return this;
    }

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    @Override
    /** 克隆对象副本 */
    public Request clone() {
        return JSONUtil.parseObject(JSON.toJSONString(this), Request.class);
    }

    /**
     * @description 工具
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class Tool implements Serializable {
        @Serial
        private static final long serialVersionUID = 5705327287643575913L;

        private String type;
        private Function function;

        /** 初始化资源或配置 */
        public static Tool init(String name, String description, JSONObject parameters) {
            return Tool.builder()
                    .type("function")
                    .function(Tool.Function.builder()
                            .name(name)
                            .description(description)
                            .parameters(parameters)
                            .build())
                    .build();
        }

        /** 初始化资源或配置 */
        public static Tool init(String name, String description, String parameters) {
            return init(name, description, JSONObject.parseObject(parameters));
        }

        /** 初始化资源或配置 */
        public static Tool init(String name, String description, Class<?> parameters) {
            return init(name, description, JSONSchemaUtil.generate(parameters));
        }

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Function implements Serializable {
            @Serial
            private static final long serialVersionUID = 8410258344985411771L;

            private String name;
            private String description;
            private JSONObject parameters;

            @Override
            public String toString() {
                return JSONObject.toJSONString(this);
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Thinking implements Serializable  {
        @Serial
        private static final long serialVersionUID = -7535502992714171138L;

        private String type;

        /** 判断是否为Enabled */
        public boolean isEnabled() {
            return "enabled".equalsIgnoreCase(type);
        }

        /** enabled操作 */
        public static Thinking enabled() {
            return Thinking.builder()
                    .type("enabled")
                    .build();
        }

        /** disabled操作 */
        public static Thinking disabled() {
            return Thinking.builder()
                    .type("disabled")
                    .build();
        }
    }

}


