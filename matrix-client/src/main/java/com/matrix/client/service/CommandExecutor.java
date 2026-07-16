package com.matrix.client.service;

import java.io.IOException;

/**
 * 指令执行器
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 * @version 1.0
 */
public interface CommandExecutor {

    String getOsInfo() throws IOException, InterruptedException;

    /**
     * @description 指令执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    String execute(String taskId, String command);

}


