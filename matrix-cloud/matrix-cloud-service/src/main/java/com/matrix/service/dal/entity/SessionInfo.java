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
 * 会话表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "tbl_session_info", autoResultMap = true)
public class SessionInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 6325434169399764534L;

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
     * 会话标题
     */
    @TableField("title")
    private String title;

    /**
     * 会话 agent
     */
    @TableField("agent")
    private String agent;

    /**
     * 会话授权级别 (继承自 user)
     */
    @TableField("auth_level")
    private Integer authLevel;

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


