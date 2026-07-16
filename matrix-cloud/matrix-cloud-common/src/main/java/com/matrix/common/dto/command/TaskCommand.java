package com.matrix.common.dto.command;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务指令
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCommand implements Serializable {
    @Serial
    private static final long serialVersionUID = 3690407605057809399L;

    private Long userId;
    private String agentName;
    private String taskId;
    private String type;
    private String body;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    public static TaskCommand convert(String json) {
        try {
            return JSONObject.parseObject(json, TaskCommand.class);
        } catch (Exception e) {
            return null;
        }
    }

}


