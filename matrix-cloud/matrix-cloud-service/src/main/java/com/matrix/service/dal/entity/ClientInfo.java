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
 * 终端表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "tbl_client_info", autoResultMap = true)
public class ClientInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1013982731067509235L;

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
     * 终端 ID
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 终端类型：pc/iot/mobile
     */
    @TableField("type")
    private String type;

    /**
     * 操作系统信息
     */
    @TableField("os_info")
    private String osInfo;

    /**
     * 状态：online/offline
     */
    @TableField("status")
    private String status;

    /**
     * 终端密钥
     */
    @TableField("secret")
    private String secret;

    /**
     * 最后心跳时间
     */
    @TableField("last_heartbeat")
    private Date lastHeartbeat;

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

    /**
     * @description 获取 LLM 提示信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String getPromptInfo() {
        return "\n- clientId: " + this.getClientId() +
                ", type: " + this.getType() +
                ", osInfo: " + this.getOsInfo();
    }

}


