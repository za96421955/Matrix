package com.matrix.gateway.filter;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.response.CommonResponse;
import com.matrix.gateway.config.LimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class LimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LimitGlobalFilter.class);
    private static final String TOTAL_RESOURCE = "gateway_total_qps";
    private static final String USER_RESOURCE = "gateway_user_qps";
    private static final String IP_RESOURCE = "gateway_ip_qps";

    private final LimitProperties limitProperties;
    private final ObjectMapper objectMapper;

    /** LimitGlobalFilter操作 */
    public LimitGlobalFilter(LimitProperties limitProperties, ObjectMapper objectMapper) {
        this.limitProperties = limitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    /** 获取排序值 */
    public int getOrder() {
        return -25;
    }

    @Override
    /** 过滤请求 */
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Info");

        if (!checkLimit(TOTAL_RESOURCE, limitProperties.getTotalQps())) {
            return onError(exchange, ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        if (!checkLimit(IP_RESOURCE + "_" + ip, limitProperties.getIpQps())) {
            return onError(exchange, ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        if (userId != null && !checkLimit(USER_RESOURCE + "_" + userId, limitProperties.getUserQps())) {
            return onError(exchange, ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        return chain.filter(exchange);
    }

    private boolean checkLimit(String resourceName, int qps) {
        Entry entry = null;
        try {
            entry = SphU.entry(resourceName, EntryType.IN);
            return true;
        } catch (BlockException e) {
            log.debug("限流触发：resource={}, qps={}", resourceName, qps);
            return false;
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        CommonResponse<Void> errorResponse = CommonResponse.error(errorCode);
        try {
            byte[] bytes = objectMapper.writeValueAsString(errorResponse).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

}


