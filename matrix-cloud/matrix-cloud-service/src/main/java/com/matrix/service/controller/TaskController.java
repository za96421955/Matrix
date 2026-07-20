package com.matrix.service.controller;

import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.response.CommonResponse;
import com.matrix.service.dal.entity.TaskInfo;
import com.matrix.service.service.task.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务控制器
 * 外部 API：/v1/task/*
 */
@Slf4j
@RestController
@RequestMapping("/v1/task")
public class TaskController {

    @Resource
    private TaskService taskService;

    /**
     * @description 获取任务详情
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    @GetMapping("/{taskId}")
    public CommonResponse<TaskInfo> getTaskInfo(@AuthenticationPrincipal UserResponse userInfo,
                                                @PathVariable("taskId") String taskId) {
        TaskInfo task = taskService.getTaskInfo(userInfo.getUserId(), taskId);
        if (task == null) {
            return CommonResponse.error(ErrorCode.TASK_NOT_FOUND);
        }
        return CommonResponse.success(task);
    }

    /**
     * @description 任务 ACK
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    @PostMapping(value = "/ack/{taskId}")
    public CommonResponse<Object> callback(@AuthenticationPrincipal UserResponse userInfo,
                                           @PathVariable("taskId") String taskId,
                                           @RequestBody(required = false) String result) throws Exception {
        taskService.callback(userInfo.getUserId(), taskId, result);
        return CommonResponse.success(null);
    }

    /**
     * @description 获取消息任务列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    @GetMapping("/waitingAuthList")
    public CommonResponse<List<TaskInfo>> getWaitingAuthList(@AuthenticationPrincipal UserResponse userInfo) {
        List<TaskInfo> taskList = taskService.getWaitingAuthList(userInfo.getUserId());
        return CommonResponse.success(taskList);
    }

    /**
     * @description 用户授权
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    @PostMapping(value = "/auth/{taskId}")
    public CommonResponse<Object> auth(@AuthenticationPrincipal UserResponse userInfo,
                                       @PathVariable("taskId") String taskId,
                                       @RequestBody(required = false) String reject) throws Exception {
        taskService.auth(userInfo.getUserId(), taskId, reject);
        return CommonResponse.success(null);
    }

}


