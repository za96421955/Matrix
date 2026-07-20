package com.matrix.local.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Command;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.RiskLevel;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.enums.RedisKey;
import com.matrix.local.service.LocalCacheService;
import com.matrix.service.context.RegisterContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 本地缓存上下文服务（SQLite 实现）
 * <p> @Primary 替代原 ServiceContext 的 Redis 实现 </p>
 *
 * @author 陈晨
 */
@Slf4j
@Primary
@Component
public class LocalRegisterContext extends RegisterContext {

    @Resource
    private LocalCacheService localCacheService;

    /**
     * @description 注册 Agent、Skill
     */
    public void register(Long userId, RegisterCommand registerCommand) {
        if (null == userId) {
            return;
        }

        // 缓存 Model
        String keyModels = RedisKey.MODELS.generateKey(userId);
        long ttlModels = RedisKey.MODELS.getTtl();
        if (null != registerCommand && StringUtils.isNotBlank(registerCommand.getApiKey())) {
            localCacheService.delete(keyModels);
            Map<String, String> hashMap = new HashMap<>();
            hashMap.put(Constant.Model.DEEPSEEK_V4_FLASH, RegisterCommand.Model.builder()
                    .baseUrl(Constant.Model.BASE_URL)
                    .apiKey(registerCommand.getApiKey())
                    .model(Constant.Model.DEEPSEEK_V4_FLASH)
                    .build()
                    .toString());
            hashMap.put(Constant.Model.DEEPSEEK_V4_PRO, RegisterCommand.Model.builder()
                    .baseUrl(Constant.Model.BASE_URL)
                    .apiKey(registerCommand.getApiKey())
                    .model(Constant.Model.DEEPSEEK_V4_PRO)
                    .build()
                    .toString());
            hashMap.forEach((field, value) ->
                    localCacheService.putHash(keyModels, field, value));
        }
        // 设置 TTL（通过一个标记 entry 记录过期时间）
        localCacheService.put("ttl:" + keyModels, String.valueOf(ttlModels), ttlModels);

        // 缓存 Skill
        String keySkills = RedisKey.SKILLS.generateKey(userId);
        long ttlSkills = RedisKey.SKILLS.getTtl();
        if (null != registerCommand && null != registerCommand.getSkills()) {
            localCacheService.delete(keySkills);
            Map<String, String> hashMap = new HashMap<>();
            for (RegisterCommand.Skill skill : registerCommand.getSkills()) {
                String skillName = skill.getName();
                if (hashMap.containsKey(skillName)) {
                    skillName = skillName + " (" +
                            skill.getClientId().substring(skill.getClientId().length() - 4) + ")";
                }
                hashMap.put(skillName, skill.toString());
            }
            hashMap.forEach((field, value) ->
                    localCacheService.putHash(keySkills, field, value));
        }
        localCacheService.put("ttl:" + keySkills, String.valueOf(ttlSkills), ttlSkills);

        // 缓存 Application
        String keyApps = RedisKey.APPS.generateKey(userId);
        long ttlApps = RedisKey.APPS.getTtl();
        if (null != registerCommand && null != registerCommand.getApps()) {
            localCacheService.delete(keyApps);
            Map<String, String> hashMap = new HashMap<>();
            for (RegisterCommand.Application app : registerCommand.getApps()) {
                hashMap.put(app.getName(), app.toString());
                // 记录风险等级
                registerCommand.getRiskLevel().getApp().put(app.getName(), app.getRiskLevel());
            }
            hashMap.forEach((field, value) ->
                    localCacheService.putHash(keyApps, field, value));
        }
        localCacheService.put("ttl:" + keyApps, String.valueOf(ttlApps), ttlApps);

        // 缓存 Risk Level
        // bash
        String keyRiskBash = RedisKey.RISK_LEVEL_BASH.generateKey(userId);
        long ttlRisk = RedisKey.RISK_LEVEL_BASH.getTtl();
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getBash()) {
            localCacheService.delete(keyRiskBash);
            registerCommand.getRiskLevel().getBash().forEach((field, value) ->
                    localCacheService.putHash(keyRiskBash, field, String.valueOf(value)));
        }
        localCacheService.put("ttl:" + keyRiskBash, String.valueOf(ttlRisk), ttlRisk);

        // tool
        String keyRiskTool = RedisKey.RISK_LEVEL_TOOL.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getTool()) {
            localCacheService.delete(keyRiskTool);
            registerCommand.getRiskLevel().getTool().forEach((field, value) ->
                    localCacheService.putHash(keyRiskTool, field, String.valueOf(value)));
        }
        localCacheService.put("ttl:" + keyRiskTool, String.valueOf(ttlRisk), ttlRisk);

        // skill
        String keyRiskSkill = RedisKey.RISK_LEVEL_SKILL.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getSkill()) {
            localCacheService.delete(keyRiskSkill);
            registerCommand.getRiskLevel().getSkill().forEach((field, value) ->
                    localCacheService.putHash(keyRiskSkill, field, String.valueOf(value)));
        }
        localCacheService.put("ttl:" + keyRiskSkill, String.valueOf(ttlRisk), ttlRisk);

        // app
        String keyRiskApp = RedisKey.RISK_LEVEL_APP.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getApp()) {
            localCacheService.delete(keyRiskApp);
            registerCommand.getRiskLevel().getApp().forEach((field, value) ->
                    localCacheService.putHash(keyRiskApp, field, String.valueOf(value)));
        }
        localCacheService.put("ttl:" + keyRiskApp, String.valueOf(ttlRisk), ttlRisk);
    }

    /**
     * @description 获取 Model
     */
    public RegisterCommand.Model getModel(Long userId, String model) {
        String value = localCacheService.getHash(RedisKey.MODELS.generateKey(userId), model);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return JSONObject.parseObject(value, RegisterCommand.Model.class);
    }

    /**
     * @description 获取 Agent 调用栈
     */
    public List<String> getAgentStack(String toolCallId) {
        String key = "agent:stack:" + toolCallId;
        String json = localCacheService.get(key);
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> stack = JSON.parseArray(json, String.class);
            if (CollectionUtils.isEmpty(stack)) {
                return Collections.emptyList();
            }
            return stack;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * @description Agent 调用压栈
     */
    public void pushAgentStack(String toolCallId, String agentName) {
        String key = "agent:stack:" + toolCallId;
        String json = localCacheService.get(key);
        List<String> stack = new ArrayList<>();
        if (StringUtils.isNotBlank(json)) {
            try {
                stack = JSON.parseArray(json, String.class);
            } catch (Exception e) {
                stack = new ArrayList<>();
            }
        }
        if (stack == null) {
            stack = new ArrayList<>();
        }
        stack.add(agentName);
        localCacheService.put(key, JSON.toJSONString(stack), RedisKey.AGENT_STACK.getTtl());
    }

    /**
     * @description Agent 调用出栈
     */
    public void popAgentStack(String toolCallId) {
        String key = "agent:stack:" + toolCallId;
        String json = localCacheService.get(key);
        if (StringUtils.isBlank(json)) {
            return;
        }
        try {
            List<String> stack = JSON.parseArray(json, String.class);
            if (CollectionUtils.isEmpty(stack) || stack.isEmpty()) {
                localCacheService.delete(key);
                return;
            }
            stack.remove(stack.size() - 1);
            if (stack.isEmpty()) {
                localCacheService.delete(key);
            } else {
                localCacheService.put(key, JSON.toJSONString(stack), RedisKey.AGENT_STACK.getTtl());
            }
        } catch (Exception e) {
            log.warn("popAgentStack error: toolCallId={}", toolCallId, e);
        }
    }

    /**
     * @description 获取 Skill 集合
     */
    public List<RegisterCommand.Skill> getSkills(Long userId) {
        if (null == userId) {
            return Collections.emptyList();
        }
        try {
            Map<String, String> values = localCacheService.getHashAll(RedisKey.SKILLS.generateKey(userId));
            if (CollectionUtils.isEmpty(values)) {
                return Collections.emptyList();
            }
            return values.values().stream()
                    .map(value -> {
                        try {
                            return JSONObject.parseObject(value, RegisterCommand.Skill.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * @description 获取 Skill
     */
    public RegisterCommand.Skill getSkill(Long userId, String name) {
        if (null == userId || StringUtils.isBlank(name)) {
            return null;
        }
        String value = localCacheService.getHash(RedisKey.SKILLS.generateKey(userId), name);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return JSONObject.parseObject(value, RegisterCommand.Skill.class);
    }

    /**
     * @description 获取 APP 集合
     */
    public List<RegisterCommand.Application> getApps(Long userId) {
        try {
            Map<String, String> values = localCacheService.getHashAll(RedisKey.APPS.generateKey(userId));
            if (CollectionUtils.isEmpty(values)) {
                return Collections.emptyList();
            }
            return values.values().stream()
                    .map(value -> {
                        try {
                            return JSONObject.parseObject(value, RegisterCommand.Application.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * @description 获取 APP
     */
    public RegisterCommand.Application getApp(Long userId, String appName) {
        String value = localCacheService.getHash(RedisKey.APPS.generateKey(userId), appName);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return JSONObject.parseObject(value, RegisterCommand.Application.class);
    }

    /**
     * @description 获取用户指令风险等级, 默认高风险
     */
    public int getCommandLevel(Long userId, String type, String command) {
        if (StringUtils.isBlank(command)) {
            return RiskLevel.HIGH;
        }
        String cmdKey = command.split(" ")[0];
        if (StringUtils.isBlank(cmdKey)) {
            return RiskLevel.HIGH;
        }
        String redisKey;
        if (Command.Type.BASH.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_BASH.generateKey(userId);
        } else if (Command.Type.TOOL.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_TOOL.generateKey(userId);
        } else if (Command.Type.AGENT.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_AGENT.generateKey(userId);
        } else if (Command.Type.SKILL.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_SKILL.generateKey(userId);
        } else if (Command.Type.APP.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_APP.generateKey(userId);
        } else {
            return RiskLevel.HIGH;
        }
        String riskLevelStr = null;
        try {
            riskLevelStr = localCacheService.getHash(redisKey, cmdKey);
            // 指令未配置, 获取 default 风险等级
            if (null == riskLevelStr) {
                riskLevelStr = localCacheService.getHash(redisKey, RiskLevel.DEFAULT);
            }
        } catch (Exception ignore) {
        }
        // default 仍然未配置, 则默认高风险
        if (riskLevelStr == null) {
            return RiskLevel.HIGH;
        }
        try {
            return Integer.parseInt(riskLevelStr);
        } catch (NumberFormatException e) {
            return RiskLevel.HIGH;
        }
    }

}
