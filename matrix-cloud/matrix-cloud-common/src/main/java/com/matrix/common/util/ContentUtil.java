package com.matrix.common.util;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * @description 内容工具
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public abstract class ContentUtil {

    private ContentUtil() {}

    /**
     * @description 生成器32位UUID
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static String getUUID() {
        String uuid = UUID.randomUUID().toString();
        return uuid.substring(0, 8)
            + uuid.substring(9, 13)
            + uuid.substring(14, 18)
            + uuid.substring(19, 23)
            + uuid.substring(24, 36);
    }

    /**
     * 对输入字符串进行 SHA-256 哈希，返回十六进制字符串
     * @param input 原始字符串（可非常长）
     * @return 64 位十六进制哈希字符串，大写或小写均可，以下返回小写
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            // 将字节数组转换为十六进制字符串
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * @description 移除 markdown 标记
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static String removeMarkers(String input, String start, String end) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        // 检查是否以开始标记开头
        if (!input.contains(start)) {
            return input;
        }

        // 找到结束标记的位置（从末尾找）
        int startIndex = StringUtils.isBlank(start) ? 0 : input.lastIndexOf(start);
        int endIndex = StringUtils.isBlank(end) ? input.length() : input.lastIndexOf(end);
        if (endIndex == -1 || endIndex <= startIndex) {
            return input;
        }

        // 截取开始标记之后、结束标记之前的内容
        int startContentIndex = startIndex + start.length();
        String content = input.substring(startContentIndex, endIndex);
        // 可选：去除内容首尾的空白字符（如换行符）
        content = content.trim();
        return content;
    }

    public static String removeJsonMarkers(String input) {
        return removeMarkers(input, "```json", "```");
    }

    public static String removeMarkdownMarkers(String input) {
        return removeMarkers(input, "```markdown", "```");
    }

}


