package com.matrix.common.dto.request;

import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 用户请求
 */
@Data
public class UserRequest {
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度 3-32 位")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6-32 位")
    private String password;
    
}


