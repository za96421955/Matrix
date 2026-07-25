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
public class Smart {

    @Description("核心目标，清晰界定'要完成什么'。")
    private String specific;

    @Description("验收标准，包含数字/比率/交付物等量化指标。")
    private String measurable;

    @Description("资源与约束条件，执行时不能跨越的界。")
    private String achievable;

    @Description("背景与意图，明确任务方向。")
    private String relevant;

    @Description("截止日期，格式：yyyy-MM-dd HH:mm:ss。")
    private String timeBound;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

}


