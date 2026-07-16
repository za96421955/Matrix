package com.matrix.common.enums;

import lombok.Getter;

/**
 * @description 资料环节
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Getter
public enum InformationPattern {
    NONE(-1, "不存在"),
    EXIT(0, "退出"),
    DONE(9999, "已完成"),

    DEMAND_ANALYZE(11, "需求分析"),
    PLAN(21, "任务规划"),
    EXECUTOR(31, "任务执行"),
    OUTPUT(41, "任务输出"),

    ;

    private final int no;
    private final String desc;

    InformationPattern(int no, String desc) {
        this.no = no;
        this.desc = desc;
    }

    public boolean eq(int no) {
        return this.getNo() == no;
    }

    /**
     * @description 获取环节提示词
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static String getPrompt() {
        StringBuilder sb = new StringBuilder();
        for (InformationPattern pattern : values()) {
            if (pattern.equals(NONE)) {
                continue;
            }
            sb.append(pattern.getNo()).append(": ").append(pattern.getDesc()).append("\n");
        }
        return sb.toString();
    }

}


