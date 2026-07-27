package com.matrix.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Skill
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill implements Serializable {
    @Serial
    private static final long serialVersionUID = 883772537521611268L;

    private String clientId;
    private String rootPath;

    private String name;
    private String description;
    private Object metadata;
    private Boolean enabled;
    private String prompt;

    /** 解析数据 */
    public static Skill parse(String yamlString) {
        // 1. 用正则分割（多行模式，匹配独立的 --- 行）
        String[] parts = yamlString.split("(?m)^---\\s*$", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid format: missing '---' separator");
        }
        String yamlPart = parts[1].trim();
        String prompt = parts[2];

        // 2. 解析YAML部分
        Map<String, Object> yamlMap = new Yaml().load(yamlPart);
        boolean enabled = null == yamlMap.get("enabled") || (Boolean) yamlMap.get("enabled");
        return Skill.builder()
                .name((String) yamlMap.get("name"))
                .description((String) yamlMap.get("description"))
                .enabled(enabled)
                .metadata(yamlMap.get("metadata"))
                .prompt(prompt.trim())
                .build();
    }
}


