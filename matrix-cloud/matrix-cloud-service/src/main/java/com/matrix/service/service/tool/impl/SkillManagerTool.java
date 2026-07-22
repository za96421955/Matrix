package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.service.service.tool.AbstractTool;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 agent 运行过程中，自动发现、管理 skill。
 * 支持查看 skill 列表、查看 SKILL.md 完整内容、创建/更新 skill、启用/禁用 skill。
 * 新增 install 操作：通过 URL 或本地文件安装 skill
 */
@Slf4j
@Component
public class SkillManagerTool extends AbstractTool<SkillManagerTool.Request> {

    @Override
    public String name() {
        return "skill-manager";
    }

    @Override
    public String description() {
        return "运行过程中，自动发现、管理 skill。" +
                "支持查看 skill 列表(名称+描述)、查看 SKILL.md 完整内容、创建/更新 skill、启用/禁用 skill。" +
                "新增 install 操作：通过 URL 或本地文件安装 skill。" +
                "脚本、元数据等其他文件，需要通过 " + Constant.CLI_TOOL_NAME + " 工具进行管理，并在 SKILL.md 中说明用法。";
    }

    @Override
    public Class<Request> requestType() {
        return Request.class;
    }

    @Override
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // 校验终端
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }
        try {
            switch (request.getOperation()) {
                case "list":
                    return this.listSkills(userId);
                case "view":
                    return this.viewSkill(request);
                case "create":
                case "update":
                    return this.createOrUpdateSkill(request);
                case "enable":
                    return this.enableSkill(request, true);
                case "disable":
                    return this.enableSkill(request, false);
                case "install":
                    return this.installSkill(request);
                default:
                    return Flux.just("不支持的操作类型: " + request.getOperation()
                            + "，支持: list / view / create / update / enable / disable / install");
            }
        } catch (Exception e) {
            log.error("userId={}, request={}, SkillManager 执行异常: {}",
                    userId, request, e.getMessage(), e);
            return Flux.just("执行失败: " + e.getMessage());
        }
    }

    /**
     * 查看 skill 列表
     */
    private Flux<String> listSkills(Long userId) {
        try {
            List<RegisterCommand.Skill> skills = registerContext.getSkills(userId);
            if (skills.isEmpty()) {
                return Flux.just("当前没有已注册的 skill");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("已注册的 skill 列表：\n");
            for (RegisterCommand.Skill skill : skills) {
                if (null == skill) {
                    continue;
                }
                sb.append("### skillName: ").append(skill.getName()).append("\n")
                        .append("-- description: ").append(skill.getDescription()).append("\n")
                        .append("-- enabled: ").append(skill.disabled() ? "false" : "true").append("\n")
                        .append("---\n");
            }
            return Flux.just(sb.toString().trim());
        } catch (Exception e) {
            log.error("获取 skill 列表异常", e);
            return Flux.just("获取 skill 列表失败: " + e.getMessage());
        }
    }

    /**
     * 查看 SKILL.md 完整内容
     */
    private Flux<String> viewSkill(Request request) {
        try {
            if (StringUtils.isBlank(request.getSkillName())) {
                return Flux.just("skillName 不可为空");
            }
            JSONObject json = new JSONObject();
            json.put("skillName", request.getSkillName());
            String command = Constant.SYSTEM_COMMAND.READ_SKILL + json.toJSONString();
            return executor.executeCommand(request.getClientId(), command)
                    .flux();
        } catch (Exception e) {
            log.error("读取 SKILL.md 异常: request={}", request, e);
            return Flux.just("读取失败: " + e.getMessage());
        }
    }

    /**
     * 创建或更新 skill
     */
    private Flux<String> createOrUpdateSkill(Request request) {
        if (StringUtils.isBlank(request.getSkillName())) {
            return Flux.just("skillName 不可为空");
        }
        if (StringUtils.isBlank(request.getDescription())) {
            return Flux.just("description 不可为空");
        }
        if (StringUtils.isBlank(request.getPrompt())) {
            return Flux.just("prompt 不可为空");
        }
        try {
            // 构建 SKILL.md 内容
            String content = this.buildSkillContent(request);
            // 写入
            JSONObject json = new JSONObject();
            json.put("skillName", request.getSkillName());
            json.put("content", content);
            String writeCommand = Constant.SYSTEM_COMMAND.WRITE_SKILL + json.toJSONString();
            String opLabel = "create".equals(request.getOperation()) ? "创建" : "更新";
            String writeResult = executor.executeCommand(request.getClientId(), writeCommand).block();
            // 触发重新注册（异步执行，不阻塞返回）
            try {
                executor.executeCommand(request.getClientId(), Constant.SYSTEM_COMMAND.TRIGGER_REGISTER).subscribe();
                return Flux.just("skill " + request.getSkillName() + " 已" + opLabel
                        + "\n写入结果: " + writeResult);
            } catch (MqttException e) {
                return Flux.just("skill " + request.getSkillName() + " 已" + opLabel
                        + "\n写入结果: " + writeResult
                        + "\n触发注册异常: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("创建/更新 skill 异常: request={}", request, e);
            return Flux.just("操作失败: " + e.getMessage());
        }
    }

    /**
     * 启用或禁用 skill
     */
    private Flux<String> enableSkill(Request request, boolean enabled) {
        if (StringUtils.isBlank(request.getSkillName())) {
            return Flux.just("skillName 不可为空");
        }
        try {
            // 先读取当前 SKILL.md
            JSONObject readJson = new JSONObject();
            readJson.put("skillName", request.getSkillName());
            String readCommand = Constant.SYSTEM_COMMAND.READ_SKILL + readJson.toJSONString();
            return executor.executeCommand(request.getClientId(), readCommand)
                    .flatMapMany(currContent -> {
                        try {
                            if (StringUtils.isBlank(currContent) || currContent.startsWith("SKILL.md not found")) {
                                return Flux.just("skill " + request.getSkillName() + " 不存在: " + currContent);
                            }
                            // 修改 YAML 头中的 enabled 字段
                            String newContent = this.modifyEnabled(currContent, enabled);
                            // 写回
                            JSONObject writeJson = new JSONObject();
                            writeJson.put("skillName", request.getSkillName());
                            writeJson.put("content", newContent);
                            String writeCommand = Constant.SYSTEM_COMMAND.WRITE_SKILL + writeJson.toJSONString();
                            return executor.executeCommand(request.getClientId(), writeCommand)
                                    .flatMapMany(writeResult -> {
                                        try {
                                            // 触发重新注册（异步执行）
                                            executor.executeCommand(request.getClientId(), Constant.SYSTEM_COMMAND.TRIGGER_REGISTER)
                                                    .subscribe();
                                            return Flux.just("skill " + request.getSkillName() + " 已" + (enabled ? "启用" : "禁用")
                                                    + "\n写入结果: " + writeResult);
                                        } catch (Exception e) {
                                            return Flux.just("skill " + request.getSkillName() + " 已" + (enabled ? "启用" : "禁用")
                                                    + "\n写入结果: " + writeResult
                                                    + "\n触发注册异常: " + e.getMessage());
                                        }
                                    });
                        } catch (Exception e) {
                            return Flux.just("操作失败: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("启用/禁用 skill 异常: request={}", request, e);
            return Flux.just("操作失败: " + e.getMessage());
        }
    }

    /**
     * 安装 skill
     * <p> 支持从 URL 下载压缩包或指定本地文件路径安装 skill </p>
     */
    private Flux<String> installSkill(Request request) {
        if (StringUtils.isBlank(request.getSource())) {
            return Flux.just("source (下载地址或本地文件路径) 不可为空");
        }
        try {
            // 构建安装参数 JSON
            JSONObject json = new JSONObject();
            json.put("source", request.getSource());
            if (StringUtils.isNotBlank(request.getSkillName())) {
                json.put("skillName", request.getSkillName());
            }
            String command = Constant.SYSTEM_COMMAND.INSTALL_SKILL + json.toJSONString();
            log.info("安装 skill: source={}, skillName={}", request.getSource(), request.getSkillName());

            // 发送到 executor 执行安装
            String result = executor.executeCommand(request.getClientId(), command).block();

            // 触发重新注册（异步执行，不阻塞返回）
            try {
                executor.executeCommand(request.getClientId(), Constant.SYSTEM_COMMAND.TRIGGER_REGISTER)
                        .subscribe();
            } catch (MqttException e) {
                log.warn("安装后触发注册异常: {}", e.getMessage());
                return Flux.just(result + "\n(安装成功，但触发 skill 注册刷新异常: " + e.getMessage() + ")");
            }
            return Flux.just(result + "\n已触发 skill 注册刷新");
        } catch (Exception e) {
            log.error("安装 skill 异常: request={}", request, e);
            return Flux.just("安装失败: " + e.getMessage());
        }
    }

    /**
     * 构建完整的 SKILL.md 内容
     */
    private String buildSkillContent(Request request) {
        String yaml = this.buildYamlContent(request);
        return "---\n" + yaml + "---\n\n" + request.getPrompt().trim();
    }

    /**
     * 构建 YAML 头内容
     * <p>SnakeYAML 默认使用 Boolean.toString() 输出小写 true/false，无需特殊处理</p>
     */
    private String buildYamlContent(Request request) {
        Map<String, Object> yamlMap = new LinkedHashMap<>();
        yamlMap.put("name", request.getSkillName());
        yamlMap.put("description", request.getDescription());
        if (StringUtils.isNotBlank(request.getAuth())) {
            yamlMap.put("auth", request.getAuth());
        }
        if (StringUtils.isNotBlank(request.getVersion())) {
            yamlMap.put("version", request.getVersion());
        }
        yamlMap.put("enabled", request.getEnabled() != null ? request.getEnabled() : true);
        if (StringUtils.isNotBlank(request.getMetadata())) {
            yamlMap.put("metadata", JSONObject.parseObject(request.getMetadata()));
        }
        return new Yaml().dumpAsMap(yamlMap);
    }

    /**
     * 修改 SKILL.md 中 YAML 头的 enabled 字段
     * <p>SnakeYAML 默认输出小写 true/false，无需特殊处理</p>
     */
    private String modifyEnabled(String fullContent, boolean enabled) {
        String[] parts = fullContent.split("(?m)^---\\s*$", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("SKILL.md 格式错误，缺少 '---' 分隔符");
        }
        String yamlPart = parts[1].trim();
        String prompt = parts[2];
        // 解析 YAML
        Yaml yaml = new Yaml();
        Map<String, Object> yamlMap = yaml.load(yamlPart);
        if (null == yamlMap) {
            throw new IllegalArgumentException("SKILL.md YAML 头解析为空");
        }
        yamlMap.put("enabled", enabled);
        // 重新序列化 YAML，SnakeYAML 默认输出小写 true/false
        String newYaml = yaml.dumpAsMap(yamlMap);
        return "---\n" + newYaml + "---\n\n" + prompt.trim();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("操作类型: list-查看skill列表, view-查看SKILL.md内容, create-创建, update-更新, enable-启用, disable-禁用, install-安装。")
        private String operation;

        @Description("skill名称，create/update/view/enable/disable 时必填。")
        private String skillName;

        @Description("安装来源：下载 URL 或本地文件路径，install 操作时必填。")
        private String source;

        @Description("skill 描述，YAML 头的 description 字段。")
        private String description;

        @Description("是否启用，YAML 头的 enabled 字段，默认 true。")
        private Boolean enabled;

        @Description("作者/维护者，YAML 头的 auth 字段。")
        private String auth;

        @Description("版本号，YAML 头的 version 字段。")
        private String version;

        @Description("额外的 YAML 元数据字段，JSON 对象格式，会合并写入 YAML 头。")
        private String metadata;

        @Description("SKILL.md 的 Prompt 主体（markdown 部分），即 --- 分隔符后的完整内容，create/update 时必填。")
        private String prompt;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


