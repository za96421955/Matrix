package com.matrix.client.service;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.client.context.Constant;
import com.matrix.client.context.MatrixClientProperties;
import com.matrix.client.util.FileUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @description 风险等级管理服务
 * <p> 管理 risk-level.yml 的读取和更新操作 </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class RiskLevelManagerService {
    private static final List<String> REJECTS = List.of("shutdown", "reboot", "sudo", "fdisk", "mkfs", "dd");
    private static final List<String> HIGHS = List.of("rm");

    @Resource
    private MatrixClientProperties properties;

    private final RegisterHeartbeatService registerHeartbeatService;
    public RiskLevelManagerService(@Lazy RegisterHeartbeatService registerHeartbeatService) {
        this.registerHeartbeatService = registerHeartbeatService;
    }

    /**
     * 获取风险等级文件的绝对路径
     */
    private File getRiskLevelFile() {
        return new File(properties.getClient().getBasic().getRiskLevelPath());
    }

    /**
     * @description 读取风险等级
     * <p> 无参数时返回完整文件内容；有参数时查询指定分类下指定指令的风险等级 </p>
     *
     * @param jsonStr JSON 参数: {"category":"bash","command":"ls"}，可为空
     * @return 风险等级信息
     */
    public String readRiskLevel(String jsonStr) {
        try {
            File riskFile = this.getRiskLevelFile();
            if (!riskFile.exists() || !riskFile.isFile()) {
                return "risk-level.yml not found: " + riskFile.getAbsolutePath();
            }
            String fullContent = FileUtil.read(riskFile.getAbsolutePath());
            if (StringUtils.isBlank(jsonStr)) {
                return fullContent;
            }
            JSONObject json = JSONObject.parseObject(jsonStr);
            String category = json.getString("category");
            String cmd = json.getString("command");
            if (StringUtils.isNotBlank(category) && StringUtils.isNotBlank(cmd)) {
                Yaml yaml = new Yaml();
                Map<String, Object> root = yaml.load(fullContent);
                if (root != null) {
                    Object categoryObj = root.get(category);
                    if (categoryObj instanceof List) {
                        for (Object item : (List<Object>) categoryObj) {
                            if (item instanceof Map) {
                                Map<String, Object> entry = (Map<String, Object>) item;
                                if (entry.containsKey(cmd)) {
                                    return cmd + ": " + entry.get(cmd);
                                }
                            }
                        }
                    }
                }
                return cmd + " not found in category: " + category;
            }
            return fullContent;
        } catch (IOException e) {
            log.error("读取风险等级文件异常", e);
            return "读取风险等级失败: " + e.getMessage();
        }
    }

    /**
     * @description 更新风险等级
     * <p> 支持 set(修改)/add(新增)/delete(删除) 三种操作，自动校验固定指令规则 </p>
     *
     * @param jsonStr JSON 参数: {"action":"set|add|delete","category":"bash","command":"ls","level":0}
     * @return 操作结果
     */
    public String updateRiskLevel(String jsonStr) {
        try {
            JSONObject json = JSONObject.parseObject(jsonStr);
            String action = json.getString("action");
            String category = json.getString("category");
            String cmd = json.getString("command");
            Integer level = json.getInteger("level");

            File riskFile = getRiskLevelFile();
            if (!riskFile.exists() || !riskFile.isFile()) {
                return "risk-level.yml not found: " + riskFile.getAbsolutePath();
            }

            // 固定指令校验 - bash 分类下固定指令不可修改
            String fixedError = this.checkFixedRules(category, cmd, action);
            if (fixedError != null) {
                return fixedError;
            }

            // 读取原始文件，提取注释头（以 # 开头的行及中间空行）
            List<String> allLines = FileUtil.readLines(riskFile.getAbsolutePath());
            StringBuilder commentHeader = new StringBuilder();
            int yamlStartLine = 0;
            for (String line : allLines) {
                if (line.trim().startsWith("#")) {
                    commentHeader.append(line).append("\n");
                    yamlStartLine++;
                } else if (line.trim().isEmpty()) {
                    commentHeader.append("\n");
                    yamlStartLine++;
                } else {
                    break;
                }
            }

            // 解析 YAML 正文
            String yamlBody = String.join("\n", allLines.subList(yamlStartLine, allLines.size()));
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(yamlBody);
            if (root == null) {
                root = new LinkedHashMap<>();
            }

            // 获取指定分类的列表
            List<Map<String, Object>> categoryList = (List<Map<String, Object>>) root.get(category);
            if (categoryList == null) {
                categoryList = new ArrayList<>();
                root.put(category, categoryList);
            }

            // 查找并操作目标指令
            int foundIndex = -1;
            for (int i = 0; i < categoryList.size(); i++) {
                if (categoryList.get(i).containsKey(cmd)) {
                    foundIndex = i;
                    break;
                }
            }
            if (foundIndex >= 0) {
                if ("delete".equals(action)) {
                    categoryList.remove(foundIndex);
                } else {
                    categoryList.get(foundIndex).put(cmd, level);
                }
            } else {
                if ("add".equals(action) || "set".equals(action)) {
                    Map<String, Object> newEntry = new LinkedHashMap<>();
                    newEntry.put(cmd, level);
                    categoryList.add(newEntry);
                } else if ("delete".equals(action)) {
                    return cmd + " not found in category: " + category;
                }
            }

            // 写回文件（保留注释头 + YAML 正文）
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            Yaml yamlWriter = new Yaml(options);
            String yamlOutput = yamlWriter.dump(root);
            FileUtil.write(riskFile.getAbsolutePath(), commentHeader + yamlOutput);

            // 触发重新注册使新配置生效
            try {
                registerHeartbeatService.reload();
                return "risk-level.yml updated and register triggered success";
            } catch (Exception e) {
                return "risk-level.yml updated but trigger register fail: " + e.getMessage();
            }
        } catch (IOException e) {
            log.error("更新风险等级文件异常", e);
            return "更新风险等级失败: " + e.getMessage();
        }
    }

    /**
     * @description 校验固定指令规则
     * <p> bash 分类下部分指令固定风险等级，不可修改或删除 </p>
     *
     * @param category 分类
     * @param cmd 指令名称
     * @param action 操作类型
     * @return 错误信息，null 表示校验通过
     */
    private String checkFixedRules(String category, String cmd, String action) {
        if (!"bash".equals(category)) {
            return null;
        }
        // 固定指令也不允许修改、删除
        if (REJECTS.contains(cmd)) {
            return "ERROR: " + cmd + " is a system-critical command, fixed at level -1 (forbidden), cannot be modified or deleted";
        }
        if (HIGHS.contains(cmd)) {
            return "ERROR: rm is fixed at level 3 (high risk) and cannot be modified or deleted";
        }
        return null;
    }

}


