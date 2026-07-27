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
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class TyphoonTool extends AbstractTool<TyphoonTool.Request> {

    /** 发报机构 -> NOAA 区域代码 */
    private static final Map<String, String> AGENCY_REGION = new HashMap<>();

    /** 洋区 -> 默认发报机构 */
    private static final Map<String, String> BASIN_AGENCY = new HashMap<>();

    static {
        AGENCY_REGION.put("pgtw", "pn");   // JTWC 关岛 - 西北太平洋
        AGENCY_REGION.put("rjtd", "jp");   // JMA 日本气象厅
        AGENCY_REGION.put("rksl", "ko");   // KMA 韩国气象厅
        AGENCY_REGION.put("knhc", "nt");   // NHC 美国国家飓风中心 - 北大西洋（默认）
        AGENCY_REGION.put("kwnh", "pa");   // CPHC 中太平洋飓风中心
        AGENCY_REGION.put("fmee", "io");   // 毛里求斯气象局 - 西南印度洋
        AGENCY_REGION.put("amsl", "sh");   // 南半球

        BASIN_AGENCY.put("wp", "pgtw");    // 西北太平洋
        BASIN_AGENCY.put("io", "fmee");    // 印度洋
        BASIN_AGENCY.put("at", "knhc");    // 北大西洋
        BASIN_AGENCY.put("ep", "knhc");    // 东北太平洋
        BASIN_AGENCY.put("cp", "kwnh");    // 中太平洋
        BASIN_AGENCY.put("sh", "amsl");    // 南半球
        BASIN_AGENCY.put("jp", "rjtd");    // 日本（JMA）
        BASIN_AGENCY.put("ko", "rksl");    // 韩国（KMA）
    }

    @Override
    /** 获取组件名称 */
    public String name() {
        return "query-typhoon";
    }

    @Override
    /** 获取组件描述 */
    public String description() {
        return "查询全球热带气旋（台风/飓风/气旋）最新预警报文，数据源为 NOAA/JTWC/JMA 等官方机构原始报文。";
    }

    @Override
    /** 获取请求参数类型 */
    public Class<Request> requestType() {
        return TyphoonTool.Request.class;
    }

    @Override
    /** 执行工具核心逻辑 */
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // 1. 终端校验
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }
        // 2. 解析参数
        String basin = StringUtils.defaultIfBlank(request.getBasin(), "wp");
        String agency = StringUtils.defaultIfBlank(request.getAgency(), BASIN_AGENCY.getOrDefault(basin, "pgtw"));
        String regionCode = resolveRegionCode(basin, agency);
        // 3. 构建命令
        String command;
        if (request.getWarningNumber() != null && request.getWarningNumber() > 0) {
            // 直接获取指定编号的警告
            command = buildFetchCommand(regionCode, request.getWarningNumber(), agency);
        } else {
            // 自动发现最新警告文件（按修改时间排序）并获取报文
            command = buildAutoFetchCommand(regionCode, agency);
        }
        // 4. 执行
        try {
            return executor.executeTask(userId, request.getClientId(), command)
                    .map(result -> {
                        // 如果指定了台风名称，在结果中过滤
                        if (StringUtils.isNotBlank(request.getStormName())) {
                            return filterByStormName(result, request.getStormName());
                        }
                        return result;
                    })
                    .onErrorResume(e -> {
                        log.error("[query-typhoon] 命令执行异常: command={}, error={}", command, e.getMessage(), e);
                        return Mono.just("执行异常：" + e.getMessage());
                    })
                    .flux();
        } catch (Exception e) {
            log.error("[query-typhoon] 命令下发异常: command={}, error={}", command, e.getMessage(), e);
            return Flux.just("执行异常：" + e.getMessage());
        }
    }

    /**
     * 解析 NOAA 区域代码
     *
     * @param basin  洋区代码
     * @param agency 发报机构代码
     * @return NOAA 2位区域代码
     */
    private String resolveRegionCode(String basin, String agency) {
        // NHC 同时负责北大西洋(nt)和东北太平洋(pz)，根据洋区区分
        if ("knhc".equals(agency)) {
            if ("ep".equals(basin)) {
                return "pz";
            }
            return "nt";
        }
        // 其他机构按映射表返回
        return AGENCY_REGION.getOrDefault(agency, "pn");
    }

    /**
     * 构建直接获取指定警告编号的命令
     */
    private String buildFetchCommand(String regionCode, int warningNumber, String agency) {
        String num = String.format("%02d", warningNumber);
        return String.format("curl -sL 'https://tgftp.nws.noaa.gov/data/raw/wt/wt%s%s.%s..txt'",
                regionCode, num, agency);
    }

    /**
     * 构建自动发现最新警告文件并获取报文的命令
     * <p>
     * 原实现按文件名编号排序取最大值（如 wtpn54），但 NOAA 服务器上遗留了大量旧台风文件，
     * 导致总是选中错误的旧文件。新实现改为解析 HTML 目录列表中的修改时间，
     * 按时间排序选取最新文件，确保获取到当前活跃台风的最新报文。
     * </p>
     */
    private String buildAutoFetchCommand(String regionCode, String agency) {
        // 月份缩写映射，用于将 "09-Jul-2026 20:11" 转为可排序的 "202607092011"
        // 注意：必须是一行，通过分号分隔多个 s/// 命令
        String monthMap = "s/Jan/01/g; s/Feb/02/g; s/Mar/03/g; s/Apr/04/g; s/May/05/g; s/Jun/06/g; s/Jul/07/g; s/Aug/08/g; s/Sep/09/g; s/Oct/10/g; s/Nov/11/g; s/Dec/12/g";

        // 用 awk 解析 HTML 表格，提取文件名和修改时间。
        // 关键修复：每行重置 f=""，避免跨行污染导致不同行的文件名和日期匹配错误。
        // 然后通过 shell 循环将日期转换为 YYYYMMDDHHMM 格式，排序取最新，
        // 最后 curl 获取该文件内容。
        return String.format(
                "MONTH_MAP='%s' && " +
                        "LATEST=$(curl -sL 'https://tgftp.nws.noaa.gov/data/raw/wt/' | " +
                        "awk -v pat='wt%s[0-9]+\\.%s\\.\\.txt' 'BEGIN{FS=\"\\\"\"} " +
                        "{f=\"\"; for(i=1;i<=NF;i++) if($i ~ pat){f=$i;break}} " +
                        "{split($0,d,\"align=\\\"right\\\">\")} " +
                        "f!=\"\" && length(d)>=2 " +
                        "{gsub(/<\\/td>.*/,\"\",d[2]); gsub(/^[ \\t]+|[ \\t]+$/,\"\",d[2]); print d[2]\"|\"f}' | " +
                        "while IFS='|' read dt fn; do " +
                        "conv=$(echo \"$dt\" | sed -E \"$MONTH_MAP\" | sed -E 's/([0-9]{2})-([0-9]{2})-([0-9]{4}) ([0-9]{2}):([0-9]{2})/\\3\\2\\1\\4\\5/'); " +
                        "echo \"$conv|$fn\"; " +
                        "done | sort -t'|' -k1rn | head -1 | cut -d'|' -f2) && " +
                        "echo \"--- 最新警告文件: $LATEST ---\" && " +
                        "curl -sL \"https://tgftp.nws.noaa.gov/data/raw/wt/$LATEST\"",
                monthMap, regionCode, agency);
    }

    /**
     * 按台风名称过滤报文内容
     *
     * @param content   原始报文
     * @param stormName 台风名称（如 BAVI、巴威、09W 等）
     * @return 过滤后的报文
     */
    private String filterByStormName(String content, String stormName) {
        if (StringUtils.isBlank(content)) {
            return content;
        }
        String[] lines = content.split("\n");
        StringBuilder matched = new StringBuilder();
        boolean found = false;
        for (String line : lines) {
            if (line.toLowerCase().contains(stormName.toLowerCase())) {
                matched.append(line).append("\n");
                found = true;
            }
        }
        if (found) {
            matched.append("\n--- 以上为匹配台风名称 \"").append(stormName).append("\" 的内容 ---\n");
            matched.append("\n--- 完整报文如下 ---\n\n");
            matched.append(content);
            return matched.toString();
        }
        // 未匹配到，返回完整报文并提示
        return "--- 未在最新警告中匹配到台风名称 \"" + stormName + "\"，以下是完整报文 ---\n\n" + content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("洋区代码：wp(西北太平洋,默认)、io(印度洋)、at(北大西洋)、ep(东北太平洋)、cp(中太平洋)、sh(南半球)、jp(日本/JMA)、ko(韩国/KMA)。")
        private String basin;

        @Description("发报机构代码：pgtw(JTWC关岛,默认)、rjtd(JMA日本气象厅)、rksl(KMA韩国气象厅)、knhc(NHC美国飓风中心)、kwnh(CPHC中太平洋)、fmee(毛里求斯气象局)。")
        private String agency;

        @Description("指定警告编号（如 31），为空时自动发现最新的警告。")
        private Integer warningNumber;

        @Description("台风名称关键词过滤（如 BAVI、巴威、09W 等），可为空。")
        private String stormName;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


