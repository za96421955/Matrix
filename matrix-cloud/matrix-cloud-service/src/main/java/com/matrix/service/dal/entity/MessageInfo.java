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
 * 消息表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "tbl_message_info", autoResultMap = true)
public class MessageInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 5878815424231120532L;

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
     * 会话 ID
     */
    @TableField("session_id")
    private Long sessionId;

    /**
     * 角色：system/user/assistant/tool
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 思考内容
     */
    @TableField("reasoning_content")
    private String reasoning_content;

    /**
     * 工具调用 (JSON)
     * List<Response.ToolCall>
     */
    @TableField("tool_calls")
    private String tool_calls;

    /**
     * 工具调用ID
     */
    @TableField("tool_call_id")
    private String tool_call_id;

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


