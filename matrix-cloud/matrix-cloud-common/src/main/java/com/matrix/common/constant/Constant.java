package com.matrix.common.constant;

/**
 * 系统常量
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Constant {

    String SYSTEM_USER = "system";
    String NEW_SESSION_TITLE = "新会话";
    String PASS = "![PASS]";

    String CLI_TOOL_NAME = "terminal";
    String CLIENT_ID = "clientId";
    String CLIENT_ID_DESCRIPTION = "终端ID。";

    /**
     * 系统指令
     */
    interface SYSTEM_COMMAND {
        String OS_INFO = "sys-read:os-info";

        String READ_MEMORY = "sys-read-memory";

        String WRITE_MEMORY = "sys-write-memory:";

        String READ_ASSISTANT = "sys-read-assistant:";

        String WRITE_ASSISTANT = "sys-write-assistant:";

        /** 读取 SKILL.md */
        String READ_SKILL = "sys-read-skill:";

        /** 写入 SKILL.md */
        String WRITE_SKILL = "sys-write-skill:";

        /** 安装 skill（支持 URL 下载或本地文件安装） */
        String INSTALL_SKILL = "sys-install-skill:";

        /** 触发 executor 立即重新注册 skill */
        String TRIGGER_REGISTER = "sys-trigger-register";

        /** 读取 risk-level.yml */
        String READ_RISK_LEVEL = "sys-read-risk-level:";

        /** 更新 risk-level.yml */
        String UPDATE_RISK_LEVEL = "sys-update-risk-level:";
    }

    /**
     * 模型
     */
    interface Model {
        String BASE_URL = "https://api.deepseek.com";
        String COMPLETIONS = "/chat/completions";

        String DEEPSEEK_V4_FLASH = "deepseek-v4-flash";
        String DEEPSEEK_V4_PRO = "deepseek-v4-pro";
    }

    /**
     * 模式
     */
    interface Pattern {

        /** 执行模式 */
        String EXECUTE = "execute";

        /** 规划模式 */
        String PLAN = "plan";

        /** 审查模式 */
        String REVIEW = "review";

        /** 目标模式 */
        String GOAL = "goal";

        /** 深度模式 */
        String DEEP = "deep";

    }

}
