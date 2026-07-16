package com.matrix.service.mqtt;

/**
 * MQTT主题
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface MqttTopics {

    String MATRIX_CLIENT_COMMAND = "matrix/client/command/+";

    String TASK_PUBLISH = "task/publish";

    String TASK_RESULT = "task/result/+";

    /** 订阅主题 */
    String[] SUBSCRIBE_TOPICS = { TASK_PUBLISH };

}


