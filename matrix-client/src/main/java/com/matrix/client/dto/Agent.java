package com.matrix.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * Agent
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent implements Serializable {
    @Serial
    private static final long serialVersionUID = 3923379276694156127L;

    private String clientId;
    private String rootPath;

    private String name;
    private String description;
    private String auth;
    private String version;
    private Boolean enabled;
    private Map<String, String> tools;
    private Map<String, String> agents;
    private Map<String, String> skills;
    private Map<String, String> apps;
    private String prompt;

    private String extend;

    public static Agent parse(String yamlString) throws IOException {
        // 1. 用正则分割（多行模式，匹配独立的 --- 行）
        String[] parts = yamlString.split("(?m)^---\\s*$", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid format: missing '---' separator");
        }
        String yamlPart = parts[1].trim();
        String prompt = parts[2];

        // 2. 解析 YAML
        Yaml yaml = new Yaml();
        Map<String, Object> yamlMap;
        try {
            yamlMap = yaml.load(yamlPart);
        } catch (YAMLException e) {
            throw new IOException("Failed to parse YAML section", e);
        }

        // 3. 构建 Agent
        boolean enabled = null == yamlMap.get("enabled") || (Boolean) yamlMap.get("enabled");
        return Agent.builder()
                .name((String) yamlMap.get("name"))
                .description((String) yamlMap.get("description"))
                .auth((String) yamlMap.get("auth"))
                .version((String) yamlMap.get("version"))
                .enabled(enabled)
                .tools(parseMap(yamlMap.get("tools")))
                .agents(parseMap(yamlMap.get("agents")))
                .skills(parseMap(yamlMap.get("skills")))
                .apps(parseMap(yamlMap.get("apps")))
                .prompt(prompt)
                .build();
    }

    public static Map<String, String> parseMap(Object list) throws IOException {
        if (!(list instanceof List)) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (Object item : (List<?>) list) {
            if (item instanceof String) {
                map.put((String) item, "");
            } else if (item instanceof Map) {
                map.putAll((Map<String, String>) item);
            } else {
                throw new IOException("Unsupported tool: " + item);
            }
        }
        return map;
    }

}


