package com.matrix.service.service.task;

import com.matrix.service.dal.entity.TaskInfo;
import org.eclipse.paho.mqttv5.common.MqttException;

import java.util.List;

/**
 * 任务服务接口
 */
public interface TaskService {

    /**
     * 查询任务详情（按 taskId）
     *
     * @param userId 用户 ID
     * @param taskId 任务 ID
     * @return 任务详情
     */
    TaskInfo getTaskInfo(Long userId, String taskId);
    
    /**
     * 查询用户等待授权的任务列表
     * 
     * @param userId 用户 ID
     * @return 任务列表
     */
    List<TaskInfo> getWaitingAuthList(Long userId);

    /**
     * @description 新增任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean insert(TaskInfo taskInfo);

    /**
     * @description 更新任务状态和结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean updateStatusAndResult(Long userId, String taskId, String status, String result);

    /**
     * @description 更新任务状态
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean updateStatus(Long userId, String taskId, String status);

    /**
     * 处理 ACK 回调
     *
     * @param taskId 任务 ID
     * @param result 执行结果
     */
    void callback(Long userId, String taskId, String result) throws Exception;

    /**
     * @description 用户授权
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    void auth(Long userId, String taskId, String reject) throws Exception;

}


