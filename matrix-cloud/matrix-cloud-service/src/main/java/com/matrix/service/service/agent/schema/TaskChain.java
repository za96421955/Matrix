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

    @Description("有序的执行块列表，按顺序执行。每个块可以是顺序块（块内任务串行）或并行块（块内任务并行）。")
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

        @Description("任务列表是否串行执行, 默认: true")
        private Boolean seq;

        @Description("该块包含的任务列表")
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

        @Description("任务信息/目标")
        private String input;

        @Description("执行结果关键产出")
        private String expectedResult;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


