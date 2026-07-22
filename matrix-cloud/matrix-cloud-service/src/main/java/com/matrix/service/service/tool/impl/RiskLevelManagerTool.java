package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.service.service.tool.AbstractTool;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 管理 risk-level.yml 风险等级配置。
 * 支持 list(查询全貌/指定分类/指定指令的风险等级)、set(设置/修改风险等级)、
 * delete(删除指令的风险等级配置)、add(新增指令的风险等级配置)。
 * 注意：bash 分类下 shutdown/reboot/sudo/fdisk/mkfs/dd 固定为 -1(禁止执行)、
 * rm 固定为 3(高风险)，不可修改或删除。
 */
@Slf4j
@Component
public class RiskLevelManagerTool extends AbstractTool<RiskLevelManagerTool.Request> {
    private static final List<String> REJECTS = List.of("shutdown", "reboot", "sudo", "fdisk", "mkfs", "dd");
    private static final List<String> HIGHS = List.of("rm");

    @Override
    public String name() {
        return "risk-level-manager";
    }

    @Override
    public String description() {
        return "管理 risk-level.yml 风险等级配置。" +
                "支持 list(查询全貌/指定分类/指定指令的风险等级)、set(设置/修改风险等级)、delete、add。";
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
                    return this.listRiskLevels(request);
                case "get":
                    return this.getRiskLevel(request);
                case "set":
                    return this.setRiskLevel(request);
                case "delete":
                    return this.deleteRiskLevel(request);
                case "add":
                    return this.addRiskLevel(request);
                default:
                    return Flux.just("不支持的操作类型: " + request.getOperation()
                            + "，支持: list / get / set / delete / add");
            }
        } catch (Exception e) {
            log.error("userId={}, request={}, RiskLevelManager 执行异常: {}",
                    userId, request, e.getMessage(), e);
            return Flux.just("执行失败: " + e.getMessage());
        }
    }

    /**
     * 查询风险等级全貌或指定分类/指令
     */
    private Flux<String> listRiskLevels(Request request) {
        try {
            JSONObject json = new JSONObject();
            if (StringUtils.isNotBlank(request.getCategory())) {
                json.put("category", request.getCategory());
            }
            if (StringUtils.isNotBlank(request.getCommand())) {
                json.put("command", request.getCommand());
            }
            String command = Constant.SYSTEM_COMMAND.READ_RISK_LEVEL + json.toJSONString();
            return executor.executeCommand(request.getClientId(), command)
                    .flux();
        } catch (Exception e) {
            log.error("查询风险等级异常: request={}", request, e);
            return Flux.just("查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定分类下指定指令的风险等级
     */
    private Flux<String> getRiskLevel(Request request) {
        if (StringUtils.isBlank(request.getCategory())) {
            return Flux.just("category(分类) 不可为空");
        }
        if (StringUtils.isBlank(request.getCommand())) {
            return Flux.just("command(指令名称) 不可为空");
        }
        try {
            JSONObject json = new JSONObject();
            json.put("category", request.getCategory());
            json.put("command", request.getCommand());
            String command = Constant.SYSTEM_COMMAND.READ_RISK_LEVEL + json.toJSONString();
            return executor.executeCommand(request.getClientId(), command)
                    .flux();
        } catch (Exception e) {
            log.error("查询风险等级异常: request={}", request, e);
            return Flux.just("查询失败: " + e.getMessage());
        }
    }

    /**
     * 设置/修改风险等级
     */
    private Flux<String> setRiskLevel(Request request) {
        if (StringUtils.isBlank(request.getCategory())) {
            return Flux.just("category(分类) 不可为空");
        }
        if (StringUtils.isBlank(request.getCommand())) {
            return Flux.just("command(指令名称) 不可为空");
        }
        if (null == request.getLevel()) {
            return Flux.just("level(风险等级) 不可为空");
        }
        // 固定规则校验
        String fixedCheck = this.checkFixedRule(request.getCategory(), request.getCommand(), request.getLevel());
        if (StringUtils.isNotBlank(fixedCheck)) {
            return Flux.just(fixedCheck);
        }
        try {
            JSONObject json = new JSONObject();
            json.put("action", "set");
            json.put("category", request.getCategory());
            json.put("command", request.getCommand());
            json.put("level", request.getLevel());
            String command = Constant.SYSTEM_COMMAND.UPDATE_RISK_LEVEL + json.toJSONString();
            return executor.executeCommand(request.getClientId(), command)
                    .flux();
        } catch (Exception e) {
            log.error("设置风险等级异常: request={}", request, e);
            return Flux.just("设置失败: " + e.getMessage());
        }
    }

    /**
     * 删除指令的风险等级配置
     */
    private Flux<String> deleteRiskLevel(Request request) {
        if (StringUtils.isBlank(request.getCategory())) {
            return Flux.just("category(分类) 不可为空");
        }
        if (StringUtils.isBlank(request.getCommand())) {
            return Flux.just("command(指令名称) 不可为空");
        }
        // 固定规则校验(删除时只校验指令名称，忽略 level)
        String fixedCheck = this.checkFixedRule(request.getCategory(), request.getCommand(), null);
        if (StringUtils.isNotBlank(fixedCheck)) {
            return Flux.just(fixedCheck);
        }
        try {
            JSONObject json = new JSONObject();
            json.put("action", "delete");
            json.put("category", request.getCategory());
            json.put("command", request.getCommand());
            String command = Constant.SYSTEM_COMMAND.UPDATE_RISK_LEVEL + json.toJSONString();
            return executor.executeCommand(request.getClientId(), command)
                    .flux();
        } catch (Exception e) {
            log.error("删除风险等级异常: request={}", request, e);
            return Flux.just("删除失败: " + e.getMessage());
        }
    }

    /**
     * 新增指令的风险等级配置
     */
    private Flux<String> addRiskLevel(Request request) {
        if (StringUtils.isBlank(request.getCategory())) {
            return Flux.just("category(分类) 不可为空");
        }
        if (StringUtils.isBlank(request.getCommand())) {
            return Flux.just("command(指令名称) 不可为空");
        }
        if (null == request.getLevel()) {
            return Flux.just("level(风险等级) 不可为空");
        }
        // 固定规则校验
        String fixedCheck = this.checkFixedRule(request.getCategory(), request.getCommand(), request.getLevel());
        if (StringUtils.isNotBlank(fixedCheck)) {
            return Flux.just(fixedCheck);
        }
        try {
            JSONObject json = new JSONObject();
            json.put("action", "add");
            json.put("category", request.getCategory());
            json.put("command", request.getCommand());
            json.put("level", request.getLevel());
            String command = Constant.SYSTEM_COMMAND.UPDATE_RISK_LEVEL + json.toJSONString();
            return executor.executeCommand(request.getClientId(), command)
                    .flux();
        } catch (Exception e) {
            log.error("新增风险等级异常: request={}", request, e);
            return Flux.just("新增失败: " + e.getMessage());
        }
    }

    /**
     * 固定规则校验
     *
     * @param category 分类
     * @param command  指令名称
     * @param level    风险等级(可为 null，仅校验固定指令)
     * @return null 表示校验通过，非 null 返回错误信息
     */
    private String checkFixedRule(String category, String command, Integer level) {
        if (StringUtils.isBlank(category) || StringUtils.isBlank(command)) {
            return null;
        }
        if (!"bash".equals(category)) {
            return null;
        }
        // 6 个系统高危指令，固定为 -1(禁止执行)
        if (REJECTS.contains(command)) {
            return "指令 " + command + " 为系统高危指令，固定风险等级为 -1(禁止执行)，不可修改/删除";
        }
        // rm 固定为 3(高风险)
        if (HIGHS.contains(command)) {
            return "指令 rm 为高风险指令，固定风险等级为 3，不可修改/删除";
        }
        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("操作类型: list-查询风险等级全貌/指定分类/指定指令, get-查询指定指令风险等级, set-设置/修改, delete-删除, add-新增。支持: list / get / set / delete / add。")
        private String operation;

        // TODO 移除 app
        @Description("分类（bash、tool、skill）。list 时可选，get/set/delete/add 时必填。")
        private String category;

        @Description("指令名称（bash cli、tool name、skill name）。get/set/delete/add 时必填，list 时可选。")
        private String command;

        @Description("风险等级：-1(禁止执行)、0(无风险)、1(低风险)、2(中风险)、3(高风险)。set/add 时必填。")
        private Integer level;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


