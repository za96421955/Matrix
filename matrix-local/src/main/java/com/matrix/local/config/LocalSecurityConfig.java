package com.matrix.local.config;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.RedisKey;
import com.matrix.common.exception.BusinessException;
import com.matrix.local.service.LocalCacheService;
import com.matrix.service.service.user.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;

/**
 * Local 安全认证配置
 * <p> 简化版 SecurityConfig，使用 LocalCacheService 替代 RedisTemplate </p>
 *
 * @author 陈晨
 */
@Configuration
@EnableWebFluxSecurity
@Slf4j
public class LocalSecurityConfig {

    @Resource
    private UserService userService;
    @Resource
    private LocalCacheService localCacheService;

    private static final String[] OPEN_PATHS = new String[] {
            "/install.sh",
            "/install.ps1",
            "/install/**",
            "/jdk/**",
            "/favicon.ico",
            "/index.html",
            "/health/check",
            "/actuator/**",
            "/v1/user/checkApiKey"
    };

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        ServerSecurityContextRepository repo = this.localTokenCachingSecurityContextRepository();
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(OPEN_PATHS).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(this.authenticationWebFilter(repo), SecurityWebFiltersOrder.AUTHENTICATION)
                .securityContextRepository(repo)
                .build();
    }

    @Bean
    public AuthenticationWebFilter authenticationWebFilter(ServerSecurityContextRepository repo) {
        AuthenticationWebFilter filter = new AuthenticationWebFilter(this.customAuthenticationManager());
        filter.setServerAuthenticationConverter(this.tokenExtractor());
        filter.setSecurityContextRepository(repo);
        return filter;
    }

    @Bean
    public ServerSecurityContextRepository localTokenCachingSecurityContextRepository() {
        return new ServerSecurityContextRepository() {
            @Override
            public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
                String token = extractToken(exchange);
                if (token != null && context.getAuthentication() != null) {
                    String key = RedisKey.AUTHORIZATION.generateKey(token);
                    String value = JSON.toJSONString(context);
                    localCacheService.put(key, value, RedisKey.AUTHORIZATION.getTtl());
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
                    String value = localCacheService.get(key);
                    context = JSON.parseObject(value, SecurityContext.class);
                } catch (Exception ignore) {}
                return context == null ? Mono.empty() : Mono.just(context);
            }

            private String extractToken(ServerWebExchange exchange) {
                return exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            }
        };
    }

    @Bean
    public ServerAuthenticationConverter tokenExtractor() {
        return exchange -> {
            String path = exchange.getRequest().getURI().getPath();
            for (String openPath : OPEN_PATHS) {
                if (openPath.contains("*")) {
                    String prefix = openPath.substring(0, openPath.indexOf("*"));
                    if (path.startsWith(prefix)) {
                        return Mono.empty();
                    }
                } else {
                    if (path.equals(openPath)) {
                        return Mono.empty();
                    }
                }
            }
            String deviceId = exchange.getRequest().getHeaders().getFirst("X-Device-Id");
            if (StringUtils.isBlank(deviceId)) {
                deviceId = "";
            }
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            return Mono.just(new UsernamePasswordAuthenticationToken(null, deviceId + "@@@" + authHeader));
        };
    }

    @Bean
    public ReactiveAuthenticationManager customAuthenticationManager() {
        return authentication -> {
            Object credentials = authentication.getCredentials();
            log.debug("Authentication credentials : {}", credentials);
            if (credentials instanceof String token) {
                try {
                    String deviceId = token.split("@@@")[0];
                    String apiKey = token.split("@@@")[1];
                    apiKey = userService.extractApiKey(apiKey);
                    UserResponse userInfo = userService.validateApiKey(deviceId, apiKey);
                    if (userInfo == null) {
                        throw new BusinessException(ErrorCode.AUTH_FAIL);
                    }
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            userInfo,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    return Mono.just(auth);
                } catch (Exception e) {
                    log.error("[认证失败] token={}, 原因: {}", token, e.getMessage(), e);
                }
                return Mono.empty();
            }
            return Mono.empty();
        };
    }

}
