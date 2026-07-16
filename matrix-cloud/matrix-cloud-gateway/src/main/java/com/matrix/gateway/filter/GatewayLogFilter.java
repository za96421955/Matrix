package com.matrix.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class GatewayLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayLogFilter.class);
    private static final String START_TIME_ATTR = "startTime";

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant startTime = Instant.now();
        exchange.getAttributes().put(START_TIME_ATTR, startTime.toEpochMilli());
        return chain.filter(exchange).doOnSuccess(aVoid -> logRequest(exchange, startTime));
    }

    private void logRequest(ServerWebExchange exchange, Instant startTime) {
        ServerHttpRequest request = exchange.getRequest();
        long costTime = System.currentTimeMillis() - startTime.toEpochMilli();
        int status = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 0;
        log.info("[Gateway] {} {} {} {}ms", request.getMethod(), request.getPath(), status, costTime);
    }

}


