package com.matrix.service.service.app;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Command;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.exception.BusinessException;
import com.matrix.common.util.FileUtil;
import com.matrix.service.context.AppContext;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.service.task.AuthService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Service;

/**
 * @description 用户应用调用服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class ApplicationService {

    @Resource
    private RegisterContext registerContext;
    @Resource
    private AppContext appContext;
    @Resource
    private AuthService authService;

    /**
     * @description 用户工具调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String call(Long userId, Long sessionId, String toolCallId, String appName, String input)
            throws MqttException {
        // 用户授权
        String result = authService.commandAuth(userId, sessionId,
                Command.Type.APP, appName, "Application: " + appName + "\n\n" + input);
        if (StringUtils.isNotBlank(result)) {
            return result;
        }
        // 获取用户应用
        RegisterCommand.Application app = registerContext.getApp(userId, appName);
        if (null == app) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND, "application " + appName + " is not exist");
        }
        // 应用调用
        JSONObject inputJson = JSONObject.parseObject(input);
        String clientId = (String) inputJson.get(Constant.CLIENT_ID);
        // 文件后缀
        String extension = StringUtils.isNotBlank(app.getExtension())
                ? app.getExtension()
                : FileUtil.getExtension(app.getPath());
        Application application = appContext.getApp(extension);
        if (null != application) {
            return application.call(userId, clientId, app, input);
        }
        throw new BusinessException(ErrorCode.APP_NOT_SUPPORT, "application " + appName + " is not support");
    }

}


