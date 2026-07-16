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
 * 用户信息表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "tbl_user_info", autoResultMap = true)
public class UserInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = -7075650094740612130L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 密码哈希
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 默认授权级别 (0-3)
     */
    @TableField("auth_level")
    private Integer authLevel;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

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


