package com.matrix.service.context;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Command;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.RiskLevel;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.enums.RedisKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 缓存上下文服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class ServiceContext {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

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
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, hashMap);
        }
        redisTemplate.expire(key, redisKey.getTtl(), TimeUnit.SECONDS);

        // 缓存 Agent
//        redisKey = RedisKey.AGENTS;
//        key = redisKey.generateKey(userId);
//        if (null != registerCommand && null != registerCommand.getAgents()) {
//            Map<String, String> hashMap = new HashMap<>();
//            for (RegisterCommand.Agent agent : registerCommand.getAgents()) {
//                hashMap.put(agent.getName(), agent.toString());
//            }
//            redisTemplate.delete(key);
//            redisTemplate.opsForHash().putAll(key, hashMap);
//        }
//        redisTemplate.expire(key, redisKey.getTtl(), TimeUnit.SECONDS);

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
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, hashMap);
        }
        redisTemplate.expire(key, redisKey.getTtl(), TimeUnit.SECONDS);

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
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, hashMap);
        }
        redisTemplate.expire(key, redisKey.getTtl(), TimeUnit.SECONDS);

        // 缓存 Risk Level
        // bash
        redisKey = RedisKey.RISK_LEVEL_BASH;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getBash()) {
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, registerCommand.getRiskLevel().getBash());
        }
        redisTemplate.expire(key, redisKey.getTtl(), TimeUnit.SECONDS);
        // tool
        redisKey = RedisKey.RISK_LEVEL_TOOL;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getTool()) {
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, registerCommand.getRiskLevel().getTool());
        }
        redisTemplate.expire(key, redisKey.getTtl(), TimeUnit.SECONDS);
        // agent
        redisKey = RedisKey.RISK_LEVEL_AGENT;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getAgent()) {
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, registerCommand.getRiskLevel().getAgent());
        }
        redisTemplate.expire(key, redisKey.getTtl(), TimeUnit.SECONDS);
        // skill
        redisKey = RedisKey.RISK_LEVEL_SKILL;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getSkill()) {
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, registerCommand.getRiskLevel().getSkill());
        }
        // app
        redisKey = RedisKey.RISK_LEVEL_APP;
        key = redisKey.generateKey(userId);
        if (null != registerCommand && null != registerCommand.getRiskLevel()
                && null != registerCommand.getRiskLevel().getApp()) {
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, registerCommand.getRiskLevel().getApp());
        }
        redisTemplate.expire(key, redisKey.getTtl(), TimeUnit.SECONDS);
    }

    /**
     * @description 获取 Model
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public RegisterCommand.Model getModel(Long userId, String model) {
        String value = (String) redisTemplate.opsForHash().get(RedisKey.MODELS.generateKey(userId), model);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return JSONObject.parseObject(value, RegisterCommand.Model.class);
    }

    /**
     * @description 获取 Agent 调用栈
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public List<String> getAgentStack(String toolCallId) {
        String key = RedisKey.AGENT_STACK.generateKey(toolCallId);
        List<Object> stack = redisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtils.isEmpty(stack)) {
            return Collections.emptyList();
        }
        return stack.stream().map(Object::toString).collect(Collectors.toList());
    }

    /**
     * @description Agent 调用压栈
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void pushAgentStack(String toolCallId, String agentName) {
        String key = RedisKey.AGENT_STACK.generateKey(toolCallId);
        redisTemplate.opsForList().rightPush(key, agentName);
        redisTemplate.expire(key, RedisKey.AGENT_STACK.getTtl(), TimeUnit.SECONDS);
    }

    /**
     * @description Agent 调用出栈
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void popAgentStack(String toolCallId) {
        String key = RedisKey.AGENT_STACK.generateKey(toolCallId);
        redisTemplate.opsForList().rightPop(key);
        redisTemplate.expire(key, RedisKey.AGENT_STACK.getTtl(), TimeUnit.SECONDS);
        // 空栈, 则删除 key
        Long size = redisTemplate.opsForList().size(key);
        if (null == size || size <= 0) {
            redisTemplate.delete(key);
        }
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
            List<Object> values = redisTemplate.opsForHash().values(RedisKey.SKILLS.generateKey(userId));
            if (CollectionUtils.isEmpty(values)) {
                return Collections.emptyList();
            }
            // 将 JSON 字符串转换为 Skill 对象
            return values.stream()
                    .map(value -> {
                        try {
                            return JSONObject.parseObject((String) value, RegisterCommand.Skill.class);
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
        String value = (String) redisTemplate.opsForHash().get(RedisKey.SKILLS.generateKey(userId), name);
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
            List<Object> values = redisTemplate.opsForHash().values(RedisKey.APPS.generateKey(userId));
            if (CollectionUtils.isEmpty(values)) {
                return Collections.emptyList();
            }
            // 将 JSON 字符串转换为 Model 对象
            return values.stream()
                    .map(value -> {
                        try {
                            return JSONObject.parseObject((String) value, RegisterCommand.Application.class);
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
        String value = (String) redisTemplate.opsForHash().get(RedisKey.APPS.generateKey(userId), appName);
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
        Integer riskLevel = null;
        try {
            riskLevel = (Integer) redisTemplate.opsForHash().get(key, cmdKey);
            // 指令未配置, 获取 default 风险等级
            if (null == riskLevel) {
                riskLevel = (Integer) redisTemplate.opsForHash().get(key, RiskLevel.DEFAULT);
            }
        } catch (Exception ignore) {
//            log.error("get risk level error: {}", e.getMessage(), e);
        }
        // default 仍然未配置, 则默认高风险
        return riskLevel == null ? RiskLevel.HIGH : riskLevel;
    }

}
