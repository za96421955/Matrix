package com.matrix.local.dal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tbl_local_timer")
public class LocalTimer {
    private Long id;
    private Long userId;
    private Long sessionId;
    private String title;
    private String content;
    private String startTime;
    private Long nextExecuteTime;
    private Integer executeCount;
    private Integer intervalSeconds;
    private Integer executedCount;
    private String status;
    private Long createTime;
}
