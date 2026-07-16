package com.matrix.service.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 任务表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "tbl_task_info", autoResultMap = true)
public class TaskInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 2494851838377630956L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * Agent ID
     */
    @TableField("agent_name")
    private String agentName;

    /**
     * 任务ID (UUID)
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 任务类型
     */
    @TableField("type")
    private String type;

    /**
     * 状态：pending/pending_auth/running/success/failed/timeout
     */
    @TableField("status")
    private String status;

    /**
     * 任务内容
     */
    @TableField("content")
    private String content;

    /**
     * 执行结果
     */
    @TableField("result")
    private String result;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 创建人 user_id
     */
    @TableField(value = "creator", fill = FieldFill.INSERT)
    private String creator;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    private Date updateTime;

    /**
     * 更新人 user_id
     */
    @TableField(value = "updator", fill = FieldFill.UPDATE)
    private String updator;

    /**
     * 数据版本号
     */
    @TableField("version_num")
    @Version
    private Integer versionNum;

    /**
     * 是否删除，0：否；1：是
     */
    @TableField("is_deleted")
//    @TableLogic
    private Boolean deleted;

}


