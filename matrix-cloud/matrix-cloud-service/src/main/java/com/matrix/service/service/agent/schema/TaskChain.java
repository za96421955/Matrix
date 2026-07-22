package com.matrix.service.service.agent.schema;

import com.alibaba.fastjson2.JSONObject;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskChain {

    @Description("任务块列表、同步执行。块内的任务列表同步/异步执行。")
    private List<ExecutionBlock> blocks;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionBlock {

        @Description("任务列表是否同步执行, 默认: true")
        private Boolean sync;

        @Description("任务块目标")
        private String goal;

        @Description("任务列表")
        private List<Task> tasks;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Task {

        @Description("工作目录（绝对路径），任务在此目录下执行。必填。")
        private String workingDirectory;

        @Description("任务名称, 驼峰变量名 (示例: taskName)")
        private String name;

        @Description("执行方案")
        private String action;

        @Description("任务目标")
        private String goal;

        @Description("关键结果")
        private String result;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


