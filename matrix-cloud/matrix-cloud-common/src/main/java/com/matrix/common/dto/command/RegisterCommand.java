package com.matrix.common.dto.command;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 注册请求
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCommand implements Serializable {
    @Serial
    private static final long serialVersionUID = -7084098956082075954L;

    private String osInfo;
    private String apiKey;
    private List<Skill> skills;
    private List<Application> apps;
    private RiskLevel riskLevel;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Model implements Serializable {
        @Serial
        private static final long serialVersionUID = -4675047861393764553L;

        private String baseUrl;
        private String apiKey;
        private String model;

        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskLevel implements Serializable {
        @Serial
        private static final long serialVersionUID = -8762067650857871999L;

        private Map<String, Integer> bash;
        private Map<String, Integer> tool;
        private Map<String, Integer> skill;
        private Map<String, Integer> app;

        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Skill implements Serializable {
        @Serial
        private static final long serialVersionUID = 883772537521611268L;

        private String clientId;
        private String rootPath;

        private String name;
        private String description;
        private Object metadata;
        private Boolean enabled;
        private String prompt;

        /** disabled操作 */
        public boolean disabled() {
            return null != enabled && !enabled;
        }

        /** 获取Prompt属性值 */
        public String getPrompt() {
            if (StringUtils.isBlank(prompt)) {
                return "";
            }
            return """
            -- exclusive clientId: %s
            -- pwd: %s
            ---
            %s
            """.formatted(clientId, rootPath, prompt);
        }

        /** 转换为Prompt格式 */
        public String toPrompt(String description) {
            if (StringUtils.isBlank(description)) {
                description = this.description;
            }
            return """
            ### skillName: %s
            -- exclusive clientId: %s
            -- pwd: %s
            -- %s
            ---
            """.formatted(name, clientId, rootPath, description);
        }

        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Application implements Serializable {
        @Serial
        private static final long serialVersionUID = -1433659680569652562L;

        private String clientId;
        private String name;
        private String description;
        private String path;
        private String extension;
        private Integer riskLevel;
        private Map<String, Object> input;

        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }
    }

}


