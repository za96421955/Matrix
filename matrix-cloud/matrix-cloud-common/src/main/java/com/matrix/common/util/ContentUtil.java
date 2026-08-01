package com.matrix.common.util;

import org.apache.commons.lang3.StringUtils;

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


