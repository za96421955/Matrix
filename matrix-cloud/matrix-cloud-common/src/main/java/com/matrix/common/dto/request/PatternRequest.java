package com.matrix.common.dto.request;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.dto.model.Request;
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
public class PatternRequest extends Request {
    @Serial
    private static final long serialVersionUID = -231313610084460317L;

    private Long userId;
    private Long sessionId;
    private String toolName;

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public PatternRequest clone() {
        return JSON.parseObject(JSON.toJSONString(this), PatternRequest.class);
    }

}


