package com.matrix.common.dto.command;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 终端执行器指令
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientCommand implements Serializable {
    @Serial
    private static final long serialVersionUID = 7644414917801820637L;

    private String taskId;
    private String clientId;
    private String command;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    /** 转换数据类型 */
    public static ClientCommand convert(String json) {
        try {
            return JSONObject.parseObject(json, ClientCommand.class);
        } catch (Exception e) {
            return null;
        }
    }

}


