package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.service.service.tool.AbstractTool;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 初始化 HTML 内容工具
 * <p> 读取源 HTML 文件，去除内联样式、class/id、脚本、事件属性、外部资源引用与注释，
 * 保留段落/标题/列表等结构标签，图片保留 alt 文本、超链接移除 href，
 * 清洗结果写入源文件同目录的 {原文件名}_init.{原文件格式}。 </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.tools.init-html", havingValue = "true")
public class InitHtmlTool extends AbstractTool<InitHtmlTool.Request> {

    /** 保留的结构标签（白名单） */
    private static final Set<String> KEEP_TAGS = Set.of(
            "html", "head", "body",
            "p", "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "dl", "dt", "dd",
            "div", "span", "section", "article", "aside",
            "header", "footer", "nav", "main", "figure", "figcaption",
            "strong", "em", "b", "i", "u", "small", "sub", "sup", "mark",
            "blockquote", "pre", "code", "address", "time", "q", "cite",
            "table", "thead", "tbody", "tfoot", "tr", "td", "th",
            "caption", "colgroup", "col", "title",
            "br", "hr", "a", "form", "fieldset", "legend", "label",
            "select", "option", "textarea");

    /** 连同内容一起丢弃的标签 */
    private static final Set<String> DROP_TAGS = Set.of("script", "style");

    /** 无关资源引用，直接丢弃标签 */
    private static final Set<String> SKIP_TAGS = Set.of("meta", "link", "base", "basefont", "param");

    /** 需要移除的属性: style / class / id */
    private static final Pattern DROP_ATTR = Pattern.compile("^(style|class|id)$", Pattern.CASE_INSENSITIVE);

    /** 事件处理属性: 以 on 开头 (onclick/onload/onchange...) */
    private static final Pattern EVENT_ATTR = Pattern.compile("^on", Pattern.CASE_INSENSITIVE);

    /** 属性解析: 支持 name、name="value"、name='value'、name=value */
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "([^\\s=/>]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]*)))?");

    @Override
    /** 获取组件名称 */
    public String name() {
        return "init-html";
    }

    @Override
    /** 获取组件描述 */
    public String description() {
        return "初始化 HTML 内容，去除样式、脚本、class/id、事件属性、外部资源引用与注释，"
                + "清洗结果写入源文件同目录 {原文件名}_init.{原文件格式}。";
    }

    @Override
    /** 获取请求参数类型 */
    public Class<Request> requestType() {
        return InitHtmlTool.Request.class;
    }

    @Override
    /** 执行工具核心逻辑 */
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // 1. ClientId 检查
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }
        // 2. 参数校验
        if (StringUtils.isBlank(request.getFilePath())) {
            return Flux.just("执行失败: filePath 不可为空");
        }
        String filePath = request.getFilePath();
        try {
            // 3. 终端读取源文件（base64 传输，避免特殊字符破坏命令）
            String readCmd = "base64 < " + shellEscape(filePath) + " | tr -d '\\r\\n'";
            log.info("[initHtml] 读取源文件 command={}", readCmd);
            return executor.executeTask(userId, request.getClientId(), readCmd)
                    .flatMap(base64Str -> {
                        try {
                            byte[] bytes = Base64.getDecoder().decode(StringUtils.deleteWhitespace(base64Str));
                            String source = new String(bytes, StandardCharsets.UTF_8);
                            // 4. Java 清洗逻辑
                            String cleaned = cleanHtml(source);
                            // 5. 输出文件: 源文件同目录 {原文件名}_init.{原文件格式}
                            String outputPath = buildOutputPath(filePath);
                            String base64Out = Base64.getEncoder().encodeToString(cleaned.getBytes(StandardCharsets.UTF_8));
                            String writeCmd = "echo " + base64Out + " | base64 -d > " + shellEscape(outputPath);
                            log.info("[initHtml] 写入输出文件 command={}", writeCmd);
                            return executor.executeTask(userId, request.getClientId(), writeCmd)
                                    .map(result -> "初始化完成: " + outputPath
                                            + "\n清洗前字符数: " + source.length()
                                            + ", 清洗后字符数: " + cleaned.length());
                        } catch (Exception e) {
                            log.error("[initHtml] 清洗或写入异常：{}", e.getMessage(), e);
                            return Mono.just("执行异常：" + e.getMessage());
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("[initHtml] 执行异常：{}", e.getMessage(), e);
                        return Mono.just("执行异常：" + e.getMessage());
                    })
                    .flux();
        } catch (Exception e) {
            log.error("[initHtml] 执行异常：{}", e.getMessage(), e);
            return Flux.just("执行异常：" + e.getMessage());
        }
    }

    /**
     * @description 初始化 HTML 内容：去除样式、脚本、无关属性与注释，保留结构标签
     * <p> 与 init_html.py 的 HTMLParser 逻辑保持一致：
     * - 删除注释、script/style 及其内容、meta/link/base/basefont/param 标签
     * - 删除 style/class/id/on* 属性，a 额外删除 href，img 仅保留 alt 文本
     * - 保留白名单结构标签，其余未知标签仅丢弃标签、保留内容 </p>
     *
     * @author 陈晨
     */
    public static String cleanHtml(String source) {
        if (source == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(source.length());
        int n = source.length();
        int textStart = 0;
        int i = 0;
        while (i < n) {
            char c = source.charAt(i);
            if (c != '<') {
                i++;
                continue;
            }
            // 命中标签或注释
            if (source.startsWith("<!--", i)) {
                // 注释: 丢弃
                out.append(source, textStart, i);
                int end = source.indexOf("-->", i + 4);
                i = end < 0 ? n : end + 3;
                textStart = i;
                continue;
            }
            if (source.startsWith("<!", i) || source.startsWith("<?", i)) {
                // DOCTYPE / 处理指令等声明: 丢弃
                out.append(source, textStart, i);
                int end = source.indexOf('>', i);
                i = end < 0 ? n : end + 1;
                textStart = i;
                continue;
            }
            // 查找标签结束位置（考虑属性值中的引号）
            int j = i + 1;
            int quote = -1;
            while (j < n) {
                char cj = source.charAt(j);
                if (quote == -1) {
                    if (cj == '"') {
                        quote = 0;
                    } else if (cj == '\'') {
                        quote = 1;
                    } else if (cj == '>') {
                        break;
                    }
                } else if ((quote == 0 && cj == '"') || (quote == 1 && cj == '\'')) {
                    quote = -1;
                }
                j++;
            }
            if (j >= n) {
                // 未闭合的 <，剩余内容按文本处理
                out.append(source, textStart, n);
                break;
            }
            String tagStr = source.substring(i + 1, j);
            out.append(source, textStart, i);
            // 处理标签，返回跳过后的位置
            int[] next = handleTag(tagStr, source, j + 1, out);
            i = next[0];
            textStart = next[1];
        }
        if (textStart < n) {
            out.append(source, textStart, n);
        }
        return out.toString();
    }

    /**
     * @description 处理单个标签，返回 {下一个扫描位置, 文本起始位置}
     * <p> script/style 开始标签会跳过至对应结束标签；meta/link 等直接丢弃；
     * img 提取 alt 文本；白名单标签过滤属性后保留 </p>
     *
     * @author 陈晨
     */
    private static int[] handleTag(String tagStr, String source, int contentStart, StringBuilder out) {
        boolean isEnd = tagStr.startsWith("/");
        String body = (isEnd ? tagStr.substring(1) : tagStr).trim();
        if (body.isEmpty()) {
            return new int[]{contentStart, contentStart};
        }
        // 自闭合标签: 去掉尾部 /
        boolean selfClosing = body.endsWith("/");
        if (selfClosing) {
            body = body.substring(0, body.length() - 1).trim();
        }
        // 提取标签名
        int sp = 0;
        while (sp < body.length() && !Character.isWhitespace(body.charAt(sp))) {
            sp++;
        }
        String tag = body.substring(0, sp).toLowerCase(Locale.ROOT);
        String attrsPart = sp < body.length() ? body.substring(sp).trim() : "";
        if (tag.isEmpty()) {
            return new int[]{contentStart, contentStart};
        }
        if (isEnd) {
            // 结束标签
            if (DROP_TAGS.contains(tag) || SKIP_TAGS.contains(tag) || "img".equals(tag)) {
                return new int[]{contentStart, contentStart};
            }
            if (KEEP_TAGS.contains(tag)) {
                out.append("</").append(tag).append(">");
            }
            return new int[]{contentStart, contentStart};
        }
        // 开始/自闭合标签
        if (DROP_TAGS.contains(tag)) {
            // 跳过 script/style 内容直到结束标签
            Matcher endMatcher = Pattern.compile("</" + Pattern.quote(tag) + "\\s*>", Pattern.CASE_INSENSITIVE)
                    .matcher(source);
            if (endMatcher.find(contentStart)) {
                int after = endMatcher.end();
                return new int[]{after, after};
            }
            return new int[]{source.length(), source.length()};
        }
        if (SKIP_TAGS.contains(tag)) {
            return new int[]{contentStart, contentStart};
        }
        if ("img".equals(tag)) {
            // 图片仅保留 alt 文本
            String alt = findAttr(attrsPart, "alt");
            if (StringUtils.isNotBlank(alt)) {
                out.append(alt);
            }
            return new int[]{contentStart, contentStart};
        }
        if (KEEP_TAGS.contains(tag)) {
            List<String[]> keep = filterAttrs(tag, parseAttrs(attrsPart));
            out.append(formatStartTag(tag, keep));
        }
        return new int[]{contentStart, contentStart};
    }

    /**
     * @description 解析标签内属性列表
     * <p> 返回 [属性名, 属性值] 列表，属性值可能为 null（无值属性，如 disabled） </p>
     *
     * @author 陈晨
     */
    private static List<String[]> parseAttrs(String attrsPart) {
        List<String[]> attrs = new ArrayList<>();
        if (StringUtils.isBlank(attrsPart)) {
            return attrs;
        }
        Matcher matcher = ATTR_PATTERN.matcher(attrsPart);
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = null;
            if (matcher.group(2) != null) {
                value = matcher.group(2);
            } else if (matcher.group(3) != null) {
                value = matcher.group(3);
            } else if (matcher.group(4) != null) {
                value = matcher.group(4);
            }
            attrs.add(new String[]{name, value});
        }
        return attrs;
    }

    /**
     * @description 查找指定属性值（不区分大小写），不存在返回 null
     *
     * @author 陈晨
     */
    private static String findAttr(String attrsPart, String attrName) {
        for (String[] kv : parseAttrs(attrsPart)) {
            if (kv[0].equalsIgnoreCase(attrName)) {
                return kv[1];
            }
        }
        return null;
    }

    /**
     * @description 过滤属性: 去掉 style/class/id/on*; a 标签额外去掉 href
     *
     * @author 陈晨
     */
    private static List<String[]> filterAttrs(String tag, List<String[]> attrs) {
        List<String[]> keep = new ArrayList<>();
        for (String[] kv : attrs) {
            String name = kv[0];
            if (DROP_ATTR.matcher(name).matches()) {
                continue;
            }
            if (EVENT_ATTR.matcher(name).find()) {
                continue;
            }
            if ("a".equals(tag) && "href".equalsIgnoreCase(name)) {
                continue;
            }
            keep.add(kv);
        }
        return keep;
    }

    /**
     * @description 格式化开始标签: 无属性 <tag>，有属性 <tag k="v">
     *
     * @author 陈晨
     */
    private static String formatStartTag(String tag, List<String[]> attrs) {
        if (attrs.isEmpty()) {
            return "<" + tag + ">";
        }
        StringBuilder sb = new StringBuilder("<").append(tag);
        for (String[] kv : attrs) {
            sb.append(' ');
            if (kv[1] == null) {
                sb.append(kv[0]);
            } else {
                sb.append(kv[0]).append("=\"").append(kv[1]).append("\"");
            }
        }
        return sb.append('>').toString();
    }

    /**
     * @description 构建输出文件路径: /dir/name.ext -> /dir/name_init.ext
     * <p> 无扩展名时: /dir/name -> /dir/name_init </p>
     *
     * @author 陈晨
     */
    public static String buildOutputPath(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return filePath;
        }
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String dir = slash >= 0 ? filePath.substring(0, slash + 1) : "";
        String name = slash >= 0 ? filePath.substring(slash + 1) : filePath;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return dir + name.substring(0, dot) + "_init." + name.substring(dot + 1);
        }
        return dir + name + "_init";
    }

    /**
     * @description 使用单引号包裹实现 shell 安全转义，防止空格、特殊字符破坏命令结构
     *
     * @author 陈晨
     */
    private static String shellEscape(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("源 HTML 文件绝对路径")
        private String filePath;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}
