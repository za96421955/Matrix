package com.matrix.client.util;

import java.util.Base64;

/**
 * Base64 工具
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public abstract class Base64Util {

    private Base64Util() {}

    /** formatHex操作 */
    public static String formatHex(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }
        // 1. 清理字符串：移除所有空白字符和换行符
        String cleanedInput = input.trim()
                .replaceAll("\\s+", "")  // 移除所有空白字符（空格、制表符、换行等）
                .replaceAll("[:\\-\\n\\r]", "");  // 移除可能的分隔符
        // 2. 验证十六进制格式
        if (!cleanedInput.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("Invalid hex string: " + cleanedInput);
        }
        // 3. 确保长度为偶数
        if (cleanedInput.length() % 2 != 0) {
            // 如果是奇数长度，可能是缺少了前导0
            cleanedInput = "0" + cleanedInput;
        }
        // 4. 将十六进制字符串转换为字节数组
        byte[] bytes = hexToByteArray(cleanedInput);
        // 5. 使用 Base64 编码，URL 安全版本
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hexToByteArray(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length: " + hex);
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);

            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("Invalid hex character at position " + i);
            }

            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }

}


