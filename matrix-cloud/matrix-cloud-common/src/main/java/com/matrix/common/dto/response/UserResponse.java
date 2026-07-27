package com.matrix.common.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 3658366447554754912L;

    /**
     * 用户 ID
     */
    private Long userId;
    
    /**
     * 用户名
     */
    private String username;

    /**
     * 默认授权级别 (0-3)
     */
    private Integer authLevel;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号（脱敏）
     */
    private String phone;

    /**
     * API Key（脱敏）
     */
    private List<ApiKey> apiKeys;

    /** 获取ApiKey属性值 */
    public String getApiKey() {
        if (CollectionUtils.isEmpty(apiKeys) || null == apiKeys.get(0)) {
            return null;
        }
        return apiKeys.get(0).getApiKey();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKey implements Serializable {
        @Serial
        private static final long serialVersionUID = -409759684943246680L;

        private String apiKey;
        private String status;
    }

}


