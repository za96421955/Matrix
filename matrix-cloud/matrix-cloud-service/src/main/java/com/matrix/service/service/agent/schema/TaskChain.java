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

    @Description("块按顺序串行执行，按里程碑或阶段拆分执行块")
    private List<ActionBlock> blocks;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionBlock {

        @Description("串行或并行, 列表中所有方案的执行过程和结果相互间完全无关可并行, 默认: true (串行)。")
        private Boolean isSerial;

        @Description("执行方案列表")
        private List<String> actions;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }

    }

}


