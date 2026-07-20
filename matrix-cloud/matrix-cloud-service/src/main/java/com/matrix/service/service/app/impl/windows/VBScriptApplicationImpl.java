package com.matrix.service.service.app.impl.windows;

import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.service.service.app.AbstractApplication;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Service;

/**
 * Windows 平台: VBScript (demo.vbs)
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class VBScriptApplicationImpl extends AbstractApplication {

    @Override
    public String fileType() {
        return "vbs";
    }

    @Override
    public String call(Long userId, String clientId,
                       RegisterCommand.Application app, String input)
            throws Exception {
        String command = "cscript '" + app.getPath() + "' '" + input + "'";
        return executor.executeTask(userId, clientId, command).block();
    }

}


