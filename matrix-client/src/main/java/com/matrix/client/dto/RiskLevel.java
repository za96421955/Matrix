package com.matrix.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskLevel implements Serializable {
    @Serial
    private static final long serialVersionUID = -8762067650857871999L;

    private Map<String, Integer> bash;
    private Map<String, Integer> tool;
    private Map<String, Integer> skill;
    private Map<String, Integer> app;

    public static RiskLevel parse(String yamlString) throws IOException {
        Map<String, Object> yamlMap = new Yaml().load(yamlString);
        List<?> bashList = (List<?>) yamlMap.get("bash");
        List<?> toolList = (List<?>) yamlMap.get("tool");
        List<?> skillList = (List<?>) yamlMap.get("skill");
        List<?> appList = (List<?>) yamlMap.get("app");
        return RiskLevel.builder()
                .bash(parseMap(bashList))
                .tool(parseMap(toolList))
                .skill(parseMap(skillList))
                .app(parseMap(appList))
                .build();
    }

    public static Map<String, Integer> parseMap(List list) throws IOException {
        Map<String, Integer> map = new HashMap<>();
        for (Object item : (List<?>) list) {
            if (item instanceof String) {
                map.put((String) item, 3);
            } else if (item instanceof Map) {
                map.putAll((Map<String, Integer>) item);
            } else {
                throw new IOException("Unsupported risk level: " + item);
            }
        }
        return map;
    }

}


