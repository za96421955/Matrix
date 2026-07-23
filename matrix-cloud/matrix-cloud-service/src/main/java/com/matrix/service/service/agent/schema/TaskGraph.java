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
public class TaskGraph {

    @Description("无序任务列表。")
    private List<Task> tasks;

    @Description("任务最终目标。")
    private String ultimateGoal;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Task {

        @Description("工作目录（绝对路径），任务在此目录下执行。必填。")
        private String workingDirectory;

        @Description("任务名称, 驼峰变量名 (示例: taskName)。")
        private String name;

        @Description("任务目标。")
        private String goal;

        @Description("执行方案。")
        private String action;

        @Description("期望结果。")
        private String expect;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


