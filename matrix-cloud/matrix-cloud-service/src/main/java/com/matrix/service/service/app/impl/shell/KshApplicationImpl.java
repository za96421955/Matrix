package com.matrix.service.service.app.impl.shell;

import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.service.service.app.AbstractApplication;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Service;

/**
 * Shell 类: Ksh (demo.ksh)
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class KshApplicationImpl extends AbstractApplication {

    @Override
    public String fileType() {
        return "ksh";
    }

    @Override
    public String call(Long userId, String clientId,
                       RegisterCommand.Application app, String input)
            throws MqttException {
        String command = "ksh '" + app.getPath() + "' '" + input + "'";
        return executor.executeTask(userId, clientId, command).block();
    }

}


