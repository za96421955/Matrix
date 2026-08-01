package com.matrix.service.service.user;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.matrix.common.constant.ClientStatus;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.SystemParam;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.dal.mapper.ClientInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 终端离线检查定时器
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@EnableScheduling
public class ClientOfflineScheduler {

    @Autowired
    private ClientInfoMapper clientInfoMapper;

    // 心跳间隔，默认60秒
    @Value("${matrix.service.mqtt.keep-alive:" + SystemParam.KEEP_ALIVE_DEFAULT + "}")
    private int keepAlive;
    // 离线判定系数
    @Value("${matrix.service.mqtt.offline-threshold:" + SystemParam.OFFLINE_THRESHOLD_DEFAULT + "}")
    private double offlineThreshold;

    /**
     * 定时检查设备离线状态
     * 每30秒执行一次
     */
    @Scheduled(fixedDelay = SystemParam.OFFLINE_CHECK_DELAY_MS)
    public void checkDeviceOffline() {
        log.debug("开始检查设备离线状态");
        try {
            // 计算离线时间阈值（毫秒）
            long offlineTimeThreshold = (long) (keepAlive * 1000 * offlineThreshold);
            // 计算离线判断的时间点
            Date offlineTimePoint = new Date(System.currentTimeMillis() - offlineTimeThreshold);
            // 构建更新条件
            ClientInfo update = new ClientInfo();
            update.setStatus(ClientStatus.OFFLINE);
            update.setUpdateTime(new Date());
            update.setUpdator(Constant.SYSTEM_USER);
            // 更新条件：状态为在线，且最后心跳时间早于离线时间点
            UpdateWrapper<ClientInfo> wrapper = new UpdateWrapper<>();
            wrapper.eq("status", ClientStatus.ONLINE)
                    .lt("last_heartbeat", offlineTimePoint);
            int rows = clientInfoMapper.update(update, wrapper);
            if (rows > 0) {
                log.debug("成功更新 {} 个设备为离线状态", rows);
            } else {
                log.debug("没有需要更新为离线的设备");
            }
        } catch (Exception e) {
            log.error("检查设备离线状态时发生异常: {}", e.getMessage(), e);
        }
    }

}


