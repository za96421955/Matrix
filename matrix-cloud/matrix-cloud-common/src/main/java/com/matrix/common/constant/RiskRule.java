package com.matrix.common.constant;

import java.util.List;

/**
 * 风险等级固定规则
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface RiskRule {

    /** bash 分类 */
    String CATEGORY_BASH = "bash";

    /** 系统高危指令（固定 -1 禁止执行） */
    List<String> REJECTS = List.of("shutdown", "reboot", "sudo", "fdisk", "mkfs", "dd");

    /** 高风险指令（固定 3） */
    List<String> HIGHS = List.of("rm");

}


