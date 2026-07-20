package com.matrix.service.service.app.impl.compile;

import com.matrix.common.util.FileUtil;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.service.service.app.AbstractApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Service;

/**
 * 编译型: Go (demo.go)
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class GoApplicationImpl extends AbstractApplication {

    @Override
    public String fileType() {
        return "go";
    }

    @Override
    public String call(Long userId, String clientId,
                       RegisterCommand.Application app, String input)
            throws Exception {
        String command;
        if (StringUtils.isBlank(FileUtil.getExtension(app.getPath()))) {
            command = "'" + app.getPath() + "' '" + input + "'";
        } else {
            command = "go run '" + app.getPath() + "' '" + input + "'";
        }
        return executor.executeTask(userId, clientId, command).block();
    }

}


