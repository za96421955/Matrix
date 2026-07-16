package com.matrix.common.constant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @description 指令类型
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Command {

    interface Type {
        String BASH = "bash";
        String TOOL = "tool";
        String AGENT = "agent";
        String SKILL = "skill";
        String APP = "app";
    }

    String[] READS = {
            // 标准输入流
            "cat", "read", "grep", "sed", "awk", "head", "tail", "wc", "sort", "uniq",
            "tr", "col", "expand", "unexpand", "xargs", "paste", "join", "nl", "pr",

            // 命令行参数（典型读取文件的命令）
            "ls", "less", "more", "cp", "mv", "tar", "find", "vim", "nano",
            "diff", "cmp", "comm", "od", "xxd", "base64", "md5sum", "sha256sum",

            // 环境变量
            "pwd", "env", "printenv", "echo", "export", "set", "declare", "which", "type", "hash",

            // 配置文件与脚本
            "source", "alias", "history", "fc", "systemctl", "crontab",

            // 终端与输入设备
            "stty", "tty", "who", "w", "last", "scriptreplay", "reset",

            // 文件描述符与特殊文件
            "dd", "mkfifo", "nc", "curl", "wget", "xclip", "pbpaste", "ipcs",

            // 其他进程通信（少量）
            "ipcrm",  // 实际也能读（-a 列表），但主要读的是 ipcs
            // 交互式增强（这些通常不是独立命令，但保留作为检索标签）
            "bind", "compgen", "complete"
    };

    Set<String> READ_SET = new HashSet<>(Arrays.asList(READS));

}


