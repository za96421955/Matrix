package com.matrix.client.service;

import com.matrix.client.context.MatrixClientProperties;
import com.matrix.client.dto.RegisterCommand;
import com.matrix.client.mqtt.MqttConnection;
import com.matrix.client.util.HttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 终端注册、心跳服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class RegisterHeartbeatService {

    private static ScheduledExecutorService heartbeatScheduler;

    @Resource
    private MatrixClientProperties properties;
    @Resource
    private CommandExecutor commandExecutor;
    @Resource
    private Fingerprint fingerprint;

    private final MqttConnection mqttConnection;
    public RegisterHeartbeatService(@Autowired(required = false) MqttConnection mqttConnection) {
        this.mqttConnection = mqttConnection;
    }

    @PostConstruct
    public void register() {
        new Thread(() -> {
            try {
                // 延迟 3 秒注册
                Thread.sleep(3000);
                // 终端注册
                int status = this.reload();
                if (status != 200) {
                    throw new RuntimeException("终端注册失败");
                }
                // 启动心跳
                this.startHeartbeat();
            } catch (Exception e) {
                log.error("Client register error: {}", e.getMessage(), e);
                this.reconnect();
            }
        }).start();
    }

    /**
     * @description MQTT 重连
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void reconnect() {
        if (null == mqttConnection) {
            return;
        }
        try {
//            this.stopHeartbeat();
            mqttConnection.disconnect();
            Thread.sleep(3000);     // 3秒后重连
            mqttConnection.connect();
        } catch (Exception ex) {
            log.error("reconnect client error", ex);
            try {
                Thread.sleep(30000);     // 30秒后重连
                this.reconnect();
            } catch (Exception ignore) {}
        }
    }

    /**
     * @description 重新加载注册信息并同步注册到平台，无延迟，不启动心跳
     * <p> 由 SkillManagerTool 等工具在创建/更新 skill 后调用，立即刷新平台缓存 </p>
     *
     * @author 陈晨
     */
    public int reload() {
        String heartbeatUrl = null;
        try {
            heartbeatUrl = properties.getService().getRegister() + "/" + fingerprint.get();
            int status = HttpClient.post(heartbeatUrl)
                    .authorization(properties.getService().getApiKey())
                    .header("X-Device-Id", fingerprint.get())
                    .body(RegisterCommand.load(commandExecutor, fingerprint, properties).toString())
                    .asStatus();
            log.info("[Heartbeat] url={}, result: {}", heartbeatUrl, status);
            return status;
        } catch (Exception e) {
            log.error("[Heartbeat] url={}, Error: {}", heartbeatUrl, e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * @description 启动心跳定时器, 刷新注册信息, 间隔 {keepAlive} 秒
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void startHeartbeat() {
        if (null != heartbeatScheduler && !heartbeatScheduler.isShutdown()) {
            return;
        }
        if (null == heartbeatScheduler) {
            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        int keepAlive = properties.getMqtt().getKeepAlive();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (null != mqttConnection && !mqttConnection.getClient().isConnected()) {
                    throw new RuntimeException("MQTT 连接已断开");
                }
                int status = this.reload();
                if (status != 200) {
                    throw new RuntimeException("心跳上报失败");
                }
            } catch (Exception e) {
                log.error("Heartbeat request failed: {}", e.getMessage(), e);
                this.reconnect();
            }
        }, keepAlive, keepAlive, TimeUnit.SECONDS);
    }

    /**
     * @description 停止心跳定时器，清除任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void stopHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
        }
        heartbeatScheduler = null;
    }

}
