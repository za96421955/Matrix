package com.matrix.common.enums;

import lombok.Getter;

/**
 * 任务模式枚举
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Getter
public enum TaskMode {

    /** 串行执行 */
    SERIAL("SERIAL"),

    /** 并行执行 */
    PARALLEL("PARALLEL"),

    /** 直接计划 */
    PLAN("PLAN"),

    /** 审查修订 */
    REVIEW("REVIEW"),

//    /** 评论修正 */
//    EVALUATION("EVALUATION")

    ;

    private final String value;

    TaskMode(String value) {
        this.value = value;
    }

}


