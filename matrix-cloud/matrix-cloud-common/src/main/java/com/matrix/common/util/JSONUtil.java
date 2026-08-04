package com.matrix.common.util;

import com.alibaba.fastjson2.JSON;

/**
 * JSON工具
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public class JSONUtil {

    private JSONUtil() {}

    public static <T> T parseObject(String json, Class<T> clazz) {
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            return JSON.parseObject(fixUnescapedInnerQuotes(json), clazz);
        }
    }

    public static String fixUnescapedInnerQuotes(String json) {
        StringBuilder sb = new StringBuilder();
        int len = json.length();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < len; i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    sb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    sb.append(c);
                    escaped = true;
                } else if (c == '"') {
                    // 在字符串内遇到未转义的双引号，判断是否是真正的字符串结束
                    // 向后跳过空白，看下一个有效字符
                    int j = i + 1;
                    while (j < len && (json.charAt(j) == ' ' || json.charAt(j) == '\t' ||
                            json.charAt(j) == '\n' || json.charAt(j) == '\r')) {
                        j++;
                    }
                    if (j < len) {
                        char next = json.charAt(j);
                        // 如果后面是 JSON 结构字符，则是真正的字符串结束
                        if (next == ',' || next == ']' || next == '}' || next == ':') {
                            inString = false;
                            sb.append(c);
                        } else {
                            // 否则是内容中的双引号，需要转义
                            sb.append('\\').append('"');
                        }
                    } else {
                        // 字符串末尾，认为是结束
                        inString = false;
                        sb.append(c);
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inString = true;
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

}


