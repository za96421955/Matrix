package com.matrix.common.dto.request;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest extends PatternRequest {
    @Serial
    private static final long serialVersionUID = 6670053863704158006L;

    private String agent;

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    /** 克隆对象副本 */
    public AgentRequest clone() {
        return JSON.parseObject(JSON.toJSONString(this), AgentRequest.class);
    }

}


