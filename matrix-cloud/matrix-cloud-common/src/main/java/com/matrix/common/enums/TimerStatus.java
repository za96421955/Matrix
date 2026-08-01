package com.matrix.common.enums;

import lombok.Getter;

/**
 * 定时任务状态枚举
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Getter
public enum TimerStatus {

    /** 进行中 */
    ACTIVE("ACTIVE"),

    /** 已完成 */
    COMPLETED("COMPLETED");

    private final String value;

    TimerStatus(String value) {
        this.value = value;
    }

}


