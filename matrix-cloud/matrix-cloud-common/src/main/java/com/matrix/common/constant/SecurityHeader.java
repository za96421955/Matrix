package com.matrix.common.constant;

/**
 * 安全认证常量
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface SecurityHeader {

    /** 设备标识请求头 */
    String DEVICE_ID_HEADER = "X-Device-Id";

    /** 认证请求头 */
    String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 前缀 */
    String BEARER_PREFIX = "Bearer ";

    /** Bearer 前缀长度 */
    int BEARER_PREFIX_LENGTH = 7;

    /** 设备标识与认证信息分隔符 */
    String TOKEN_SEPARATOR = "@@@";

    /** 用户角色 */
    String ROLE_USER = "ROLE_USER";

    /** 公开路径 */
    String[] OPEN_PATHS = new String[] {
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

}


