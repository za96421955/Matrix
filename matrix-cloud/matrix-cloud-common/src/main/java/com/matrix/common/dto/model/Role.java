package com.matrix.common.dto.model;

/**
 * 模型消息角色
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Role {

    /** 非模型角色类型, 在ReAct过程中用于标识一段对话结束 */
    String DONE = "done";

    String SYSTEM = "system";

    String USER = "user";

    String ASSISTANT = "assistant";

    String TOOL = "tool";

//    String LATEST_REMINDER = "latest_reminder";

}


