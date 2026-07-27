package com.matrix.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @description Tool
 * <p> <功能详细描述> </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application implements Serializable {
    @Serial
    private static final long serialVersionUID = -4531143241070652709L;

    private String clientId;

    private String name;
    private String description;
    private String path;
    private String extension;
    private Integer riskLevel;
    private Map<String, Object> input;

    /** 解析数据 */
    public static Application parse(String clientId, String rootPath, String yamlString) {
        Map<String, Object> yamlMap = new Yaml().load(yamlString);
        return Application.builder()
                .clientId(clientId)
                .name((String) yamlMap.get("name"))
                .description((String) yamlMap.get("description"))
                .path(resolveToAbsolute(rootPath, (String) yamlMap.get("path")))
                .extension((String) yamlMap.get("extension"))
                .riskLevel((Integer) yamlMap.get("risk-level"))
                .input((Map<String, Object>) yamlMap.get("input"))
                .build();
    }

    /**
     * 解析路径字符串，返回绝对路径
     * - ./xxx 或 xxx 等相对路径 → 基于当前工作目录拼接
     * - ~/xxx → 展开为用户家目录
     * - 其他绝对路径 → 原样返回（经规范化）
     */
    public static String resolveToAbsolute(String rootPath, String rawPath) {
        if (StringUtils.isBlank(rawPath)) {
            return rawPath;
        }
        // 1. 处理 ~/ 或 ~ 开头，替换为用户家目录
        if (rawPath.startsWith("~/")) {
            String home = System.getProperty("user.home");
            rawPath = home + rawPath.substring(1);   // 去掉 ~，保留 /
        } else if (rawPath.equals("~")) {
            rawPath = System.getProperty("user.home");
        }
        // 2. 转为 Path 对象
        Path path = Paths.get(rawPath);
        // 3. 如果是绝对路径，规范化后直接返回
        if (path.isAbsolute()) {
            return path.normalize().toString();
        }
        // 4. 相对路径：与当前工作目录拼接
        Path currentDir = Paths.get(rootPath);
        Path resolved = currentDir.resolve(path).normalize();
        return resolved.toString();
    }

//    public static void main(String[] args) {
//        String rootPath = System.getProperty("user.dir");
//        System.out.println(resolveToAbsolute(rootPath, "./Awk_print.awk"));
//        System.out.println(resolveToAbsolute(rootPath, "Awk_print.awk"));
//        System.out.println(resolveToAbsolute(rootPath, "~/apps/Awk_print.awk"));
//        System.out.println(resolveToAbsolute(rootPath, "/etc/hosts"));   // 绝对路径直接返回
//    }

}


