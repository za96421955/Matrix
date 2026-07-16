package com.matrix.common.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Session 列表查询请求（操作清单 3.1）
 */
@Data
public class SessionRequest {
    
    @Min(1)
    private Integer pageNum = 1;
    
    @Min(1)
    @Max(100)
    private Integer pageSize = 20;
    
    private String agentName;

    private String title;

    @Min(0)
    @Max(3)
    private Integer authLevel;

}


