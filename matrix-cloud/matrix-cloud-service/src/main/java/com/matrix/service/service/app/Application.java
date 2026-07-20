package com.matrix.service.service.app;

import com.matrix.common.dto.command.RegisterCommand;
import org.eclipse.paho.mqttv5.common.MqttException;

/**
 * 用户工具调用
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Application {

    /**
     * @description 文件类型
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    String fileType();

    /**
     * @description 应用调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    String call(Long userId, String clientId, RegisterCommand.Application app, String input)
            throws Exception;

}


