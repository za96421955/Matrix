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
public class Actions {

    @Description("执行方案列表")
    private List<String> actions;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

}


