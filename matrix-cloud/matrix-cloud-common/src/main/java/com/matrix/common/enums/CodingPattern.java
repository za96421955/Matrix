package com.matrix.common.enums;

import lombok.Getter;

/**
 * @description 编程环节
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Getter
public enum CodingPattern {
    NONE(-1, "不存在"),
    EXIT(0, "退出"),
    DONE(9999, "已完成"),

    // P1.1. 项目路径检查（goto P1.1）
//    NEW(11, "新任务, 项目路径检查"),

    // TODO P2.1. 需求分析
    DEMAND_ANALYZE(21, "需求分析"),
    // TODO P2.3. 需求检查（goto P2.1）
//    DEMAND_REVIEW(23, "需求检查"),
    // TODO P2.4.
//    DEMAND_XXXXXX(23, "评审检查"),

    // TODO P3.1. 开发任务规划（plan）
    PLAN_DEVELOP(31, "开发任务规划"),
    // TODO P3.2. 测试任务规划（plan）
//    PLAN_TEST(32, "测试任务规划"),
    // TODO P3.3. 部署任务规划（plan）
//    PLAN_DEPLOY(33, "部署任务规划"),
    // TODO P3.4. 其他任务规划（plan）
//    PLAN_OTHER(34, "其他任务规划"),

    // TODO P4.1. 开发任务执行
    DEVELOP_EXECUTOR(41, "开发任务执行"),
    // TODO P4.3. 代码 Review（goto P4.1）
//    DEVELOP_REVIEW(43, "代码 Review"),
    // TODO P4.4.
//    DEVELOP_XXXXXX(44, "问题修复"),

    // TODO P5.1. 测试任务执行（goto P4.1）
//    TEST_EXECUTOR(51, "测试任务执行"),
    // TODO P5.3.
//    TEST_XXXXXX(53, "测试问题修复"),

    // TODO P6.1. 部署任务执行
//    DEPLOY_EXECUTOR(61, "部署任务执行"),
    // TODO P6.2. 部署结果检查（goto P6.1）
//    DEPLOY_REVIEW(62, "部署结果检查"),
    // TODO P6.3.
//    DEPLOY_XXXXXX(63, "部署问题修复"),

    // TODO P7.1. 其他任务执行
//    EXECUTOR_OTHER(71, "其他任务执行"),

    ;

    private final int no;
    private final String desc;

    CodingPattern(int no, String desc) {
        this.no = no;
        this.desc = desc;
    }

    /** eq操作 */
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
        for (CodingPattern pattern : values()) {
            if (pattern.equals(NONE)) {
                continue;
            }
            sb.append(pattern.getNo()).append(": ").append(pattern.getDesc()).append("\n");
        }
        return sb.toString();
    }

}


