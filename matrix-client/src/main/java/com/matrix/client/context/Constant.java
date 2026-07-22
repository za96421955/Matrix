package com.matrix.client.context;

/**
 * 常量
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Constant {

    String MEMORY = "MEMORY.md";
    String ASSISTANT = ".ASSISTANT.md";

    String AGENT_FILE = "AGENT.md";
    String AGENT_EXTEND = "EXTEND.md";

    String SKILL_FILE = "SKILL.md";

    String APP_FILE = "APPLICATION.yml";

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

}
