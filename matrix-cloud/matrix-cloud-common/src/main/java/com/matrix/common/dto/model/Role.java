package com.matrix.common.dto.model;

/**
 * 模型消息角色
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Role {

    String SYSTEM = "system";

    String USER = "user";

    String ASSISTANT = "assistant";

    String TOOL = "tool";

//    String LATEST_REMINDER = "latest_reminder";



    /**
     * 自定义角色
     */
    String FLAG = "flag";

    String ERROR = "error";

}
