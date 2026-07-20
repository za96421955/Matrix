package com.matrix.service.service.task;

/**
 * 任务发布, 等待执行结果
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface TaskComplete {

    /**
     * @description 更新任务结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    void completeTask(Long userId, String taskId, String result) throws Exception;

}
