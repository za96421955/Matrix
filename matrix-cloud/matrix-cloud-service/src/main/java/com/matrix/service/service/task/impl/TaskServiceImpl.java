package com.matrix.service.service.task.impl;

import com.matrix.common.constant.Constant;
import com.matrix.service.context.TaskContext;
import com.matrix.service.dal.entity.TaskInfo;
import com.matrix.service.service.task.TaskPublish;
import com.matrix.service.service.task.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 任务服务实现类
 * <p>基于 Redis 持久化，通过 TaskContext 统一管理任务数据</p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskContext taskContext;

    @Resource
    private TaskPublish taskPublish;

    @Override
    public TaskInfo getTaskInfo(Long userId, String taskId) {
        if (null == userId || StringUtils.isBlank(taskId)) {
            return null;
        }
        return taskContext.getTaskInfo(taskId);
    }

    @Override
    public List<TaskInfo> getWaitingAuthList(Long userId) {
        if (null == userId) {
            return Collections.emptyList();
        }
        return taskContext.getWaitingAuthList(userId);
    }

    @Override
    public boolean insert(TaskInfo taskInfo) {
        if (null == taskInfo) {
            return false;
        }
        try {
            taskContext.insert(taskInfo);
            return true;
        } catch (Exception e) {
            log.error("[TaskService] insert 异常, taskId={}", taskInfo.getTaskId(), e);
            return false;
        }
    }

    @Override
    public boolean updateStatusAndResult(Long userId, String taskId, String status, String result) {
        if (null == userId || StringUtils.isBlank(taskId) || StringUtils.isBlank(status)) {
            return false;
        }
        try {
            taskContext.updateStatusAndResult(userId, taskId, status, result);
            return true;
        } catch (Exception e) {
            log.error("[TaskService] updateStatusAndResult 异常, taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public boolean updateStatus(Long userId, String taskId, String status) {
        return this.updateStatusAndResult(userId, taskId, status, null);
    }

    @Override
    public void callback(Long userId, String taskId, String result) throws MqttException {
        log.info("[终端ACK] userId={}, taskId={}, result={}", userId, taskId, result);
        taskPublish.completeTask(userId, taskId, result);
    }

    @Override
    public void auth(Long userId, String taskId, String reject) throws MqttException {
        log.info("[用户授权] userId={}, taskId={}, reject={}", userId, taskId, reject);
        if (StringUtils.isBlank(reject)) {
            reject = Constant.PASS;
        }
        taskPublish.completeTask(userId, taskId, reject);
    }

}
