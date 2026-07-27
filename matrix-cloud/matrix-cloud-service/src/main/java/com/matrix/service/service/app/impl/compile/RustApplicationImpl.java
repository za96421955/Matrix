package com.matrix.service.service.app.impl.compile;

import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.service.service.app.AbstractApplication;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Service;

/**
 * 编译型: Rust — 使用 cargo-script 或 rust-script（demo.rs）
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class RustApplicationImpl extends AbstractApplication {

    @Override
    /** fileType操作 */
    public String fileType() {
        return "rs";
    }

    @Override
    /** call操作 */
    public String call(Long userId, String clientId,
                       RegisterCommand.Application app, String input)
            throws Exception {
        String command = "rust-script '" + app.getPath() + "' '" + input + "'";
        return executor.executeTask(userId, clientId, command).block();
    }

}


