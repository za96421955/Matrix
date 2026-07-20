package com.matrix.service.security;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.enums.RedisKey;
import com.matrix.service.cache.ServiceCache;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 基于 Redis 的 Token 认证缓存仓库
 * - 缓存键：auth:token:<Authorization 值>
 * - TTL：5 分钟
 */
public class AuthorizationContext implements ServerSecurityContextRepository {

    private final ServiceCache serviceCache;

    public AuthorizationContext(ServiceCache serviceCache) {
        this.serviceCache = serviceCache;
    }

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        String token = extractToken(exchange);
        if (token != null && context.getAuthentication() != null) {
            String key = RedisKey.AUTHORIZATION.generateKey(token);
            String value = JSON.toJSONString(context);
            serviceCache.set(key, value, RedisKey.AUTHORIZATION.getTtl());
        }
        return Mono.empty();
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        String token = extractToken(exchange);
        if (token == null) {
            return Mono.empty();
        }
        String key = RedisKey.AUTHORIZATION.generateKey(token);
        SecurityContext context = null;
        try {
            String value = serviceCache.get(key);
            context = JSON.parseObject(value, SecurityContext.class);
        } catch (Exception ignore) {}
        return context == null ? Mono.empty() : Mono.just(context);
    }

    private String extractToken(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    }

}


