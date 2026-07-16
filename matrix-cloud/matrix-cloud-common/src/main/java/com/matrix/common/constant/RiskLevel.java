package com.matrix.common.constant;

/**
 * @description 风险等级
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface RiskLevel {

    String DEFAULT = "default";

    /** 禁止执行 */
    int DISABLE = -1;

    /** 无风险 */
    int NONE = 0;

    /** 底风险 */
    int LOW = 1;

    /** 中风险 */
    int MEDIUM = 2;

    /** 高风险 */
    int HIGH = 3;

}


