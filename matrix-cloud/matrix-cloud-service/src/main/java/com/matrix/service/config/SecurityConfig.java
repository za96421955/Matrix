package com.matrix.service.config;

import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.exception.BusinessException;
import com.matrix.service.service.user.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.util.Collections;

/**
 * 安全认证
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Configuration
@EnableWebFluxSecurity
@Slf4j
public class SecurityConfig {

    @Resource
    private UserService userService;
    @Resource
    protected RedisTemplate<String, Object> redisTemplate;

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
        ServerSecurityContextRepository repo = this.tokenCachingSecurityContextRepository();
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // 放行注册、登录、获取 token 的接口
                        .pathMatchers(OPEN_PATHS).permitAll()
                        // 其他所有请求都需要认证
                        .anyExchange().authenticated()
                )
                .addFilterAt(this.authenticationWebFilter(repo), SecurityWebFiltersOrder.AUTHENTICATION)
                .securityContextRepository(repo)
                .build();
    }

    /**
     * 自定义认证过滤器：从 Authorization 请求头提取 token，并调用认证管理器
     */
    @Bean
    public AuthenticationWebFilter authenticationWebFilter(ServerSecurityContextRepository repo) {
        AuthenticationWebFilter filter = new AuthenticationWebFilter(this.customAuthenticationManager());
        filter.setServerAuthenticationConverter(this.tokenExtractor());
        filter.setSecurityContextRepository(repo);
        return filter;
    }

    @Bean
    public ServerSecurityContextRepository tokenCachingSecurityContextRepository() {
        return new TokenCachingSecurityContextRepository(redisTemplate);
    }

    /**
     * 从请求中提取 token 并创建未认证的 Authentication 对象
     */
    @Bean
    public ServerAuthenticationConverter tokenExtractor() {
        return exchange -> {
            String path = exchange.getRequest().getURI().getPath();
            for (String openPath : OPEN_PATHS) {
                if (openPath.contains("*")) {
                    // Ant风格: /install/**匹配 /install/开头的路径
                    String prefix = openPath.substring(0, openPath.indexOf("*"));
                    if (path.startsWith(prefix)) {
                        return Mono.empty(); //公开路径，直接跳过
                    }
                } else {
                    if (path.equals(openPath)) {
                        return Mono.empty(); //公开路径，直接跳过
                    }
                }
            }
            //提取 header
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
            // 从 authentication 的 credentials 中取出原始 token
            Object credentials = authentication.getCredentials();
            log.debug("Authentication credentials : {}", credentials);
            if (credentials instanceof String token) {
                try {
                    String deviceId = token.split("@@@")[0];
                    String apiKey = token.split("@@@")[1];
                    apiKey = userService.extractApiKey(apiKey);
                    // API Key 认证
                    UserResponse userInfo = userService.validateApiKey(deviceId, apiKey);
                    if (userInfo == null) {
                        throw new BusinessException(ErrorCode.AUTH_FAIL);
                    }
                    // 构建认证成功的 Authentication 对象
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            userInfo,         // principal
                            null,             // credentials (清空)
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    return Mono.just(auth);
                } catch (Exception e) {
                    log.error("[认证失败] token={}, 原因: {}", token, e.getMessage(), e);
                }
                return Mono.empty(); // 认证失败
            }
            return Mono.empty();
        };
    }

}


