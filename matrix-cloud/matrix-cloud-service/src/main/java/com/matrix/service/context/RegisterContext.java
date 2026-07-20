package com.matrix.service.context;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Command;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.RiskLevel;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.enums.RedisKey;
import com.matrix.service.cache.ServiceCache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 缓存上下文服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class RegisterContext {

    @Resource
    private ServiceCache serviceCache;

    /**
     * @description 注册 Agent、Skill
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void register(Long userId, RegisterCommand registerCommand) {
        if (null == userId) {
            return;
        }

        // 缓存 Model
        RedisKey redisKey = RedisKey.MODELS;
        String key = redisKey.generateKey(userId);
        if (null != registerCommand && StringUtils.isNotBlank(registerCommand.getApiKey())) {
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
            serviceCache.delete(key);
            serviceCache.getHash().putAll(key, hashMap, redisKey.getTtl());
        }

        // 缓存 Skill
        redisKey = RedisKey.SKILLS;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getSkills()) {
            Map<String, String> hashMap = new HashMap<>();
            for (RegisterCommand.Skill skill : registerCommand.getSkills()) {
                String skillName = skill.getName();
                if (hashMap.containsKey(skillName)) {
                    skillName = skillName + " (" +
                            skill.getClientId().substring(skill.getClientId().length() - 4) + ")";
                }
                hashMap.put(skillName, skill.toString());
            }
            serviceCache.delete(key);
            serviceCache.getHash().putAll(key, hashMap, redisKey.getTtl());
        }

        // 缓存 Application
        redisKey = RedisKey.APPS;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getApps()) {
            Map<String, String> hashMap = new HashMap<>();
            for (RegisterCommand.Application app : registerCommand.getApps()) {
                hashMap.put(app.getName(), app.toString());
                // 记录风险等级
                registerCommand.getRiskLevel().getApp().put(app.getName(), app.getRiskLevel());
            }
            serviceCache.delete(key);
            serviceCache.getHash().putAll(key, hashMap, redisKey.getTtl());
        }

        // 缓存 Risk Level
        // bash
        redisKey = RedisKey.RISK_LEVEL_BASH;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getBash()) {
            serviceCache.delete(key);
            serviceCache.getHash().putAll(key, registerCommand.getRiskLevel().getBash(),
                    redisKey.getTtl());
        }
        // tool
        redisKey = RedisKey.RISK_LEVEL_TOOL;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getTool()) {
            serviceCache.delete(key);
            serviceCache.getHash().putAll(key, registerCommand.getRiskLevel().getTool(),
                    redisKey.getTtl());
        }
        // app
        redisKey = RedisKey.RISK_LEVEL_APP;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getApp()) {
            serviceCache.delete(key);
            serviceCache.getHash().putAll(key, registerCommand.getRiskLevel().getApp(),
                    redisKey.getTtl());
        }
    }

    /**
     * @description 获取 Model
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public RegisterCommand.Model getModel(Long userId, String model) {
        String value = serviceCache.getHash().get(RedisKey.MODELS.generateKey(userId), model);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return JSONObject.parseObject(value, RegisterCommand.Model.class);
    }

    /**
     * @description 获取 Skill 集合
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public List<RegisterCommand.Skill> getSkills(Long userId) {
        if (null == userId) {
            return Collections.emptyList();
        }
        try {
            // 获取 Hash 中的所有 value（JSON 字符串）
            Set<String> values = serviceCache.getHash().values(RedisKey.SKILLS.generateKey(userId));
            if (CollectionUtils.isEmpty(values)) {
                return Collections.emptyList();
            }
            // 将 JSON 字符串转换为 Skill 对象
            return values.stream()
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
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public RegisterCommand.Skill getSkill(Long userId, String name) {
        if (null == userId || StringUtils.isBlank(name)) {
            return null;
        }
        String value = serviceCache.getHash().get(RedisKey.SKILLS.generateKey(userId), name);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return JSONObject.parseObject(value, RegisterCommand.Skill.class);
    }

    /**
     * @description 获取 APP 集合
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public List<RegisterCommand.Application> getApps(Long userId) {
        try {
            // 获取 Hash 中的所有 value（JSON 字符串）
            Set<String> values = serviceCache.getHash().values(RedisKey.APPS.generateKey(userId));
            if (CollectionUtils.isEmpty(values)) {
                return Collections.emptyList();
            }
            // 将 JSON 字符串转换为 Model 对象
            return values.stream()
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
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public RegisterCommand.Application getApp(Long userId, String appName) {
        String value = serviceCache.getHash().get(RedisKey.APPS.generateKey(userId), appName);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return JSONObject.parseObject(value, RegisterCommand.Application.class);
    }

    /**
     * @description 获取用户指令风险等级, 默认高风险
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public int getCommandLevel(Long userId, String type, String command) {
        if (StringUtils.isBlank(command)) {
            return RiskLevel.HIGH;
        }
        String cmdKey = command.split(" ")[0];
        if (StringUtils.isBlank(cmdKey)) {
            return RiskLevel.HIGH;
        }
        RedisKey redisKey = null;
        if (Command.Type.BASH.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_BASH;
        }
        if (Command.Type.TOOL.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_TOOL;
        }
        if (Command.Type.AGENT.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_AGENT;
        }
        if (Command.Type.SKILL.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_SKILL;
        }
        if (Command.Type.APP.equals(type)) {
            redisKey = RedisKey.RISK_LEVEL_APP;
        }
        if (null == redisKey) {
            return RiskLevel.HIGH;
        }
        String key = redisKey.generateKey(userId);
        String riskLevel = null;
        try {
            riskLevel = serviceCache.getHash().get(key, cmdKey);
            // 指令未配置, 获取 default 风险等级
            if (null == riskLevel) {
                riskLevel = serviceCache.getHash().get(key, RiskLevel.DEFAULT);
            }
        } catch (Exception ignore) {
//            log.error("get risk level error: {}", e.getMessage(), e);
        }
        // default 仍然未配置, 则默认高风险
        return riskLevel == null ? RiskLevel.HIGH : Integer.parseInt(riskLevel);
    }

}


