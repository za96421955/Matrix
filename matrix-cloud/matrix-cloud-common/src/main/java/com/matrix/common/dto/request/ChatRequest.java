package com.matrix.common.dto.request;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.constant.RiskLevel;
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
public class ChatRequest extends AgentRequest {
    @Serial
    private static final long serialVersionUID = 7126402777618158445L;

    private String pattern;
    private Integer authLevel = RiskLevel.NONE;

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    /** 克隆对象副本 */
    public ChatRequest clone() {
        return JSON.parseObject(JSON.toJSONString(this), ChatRequest.class);
    }

}


