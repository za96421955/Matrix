package com.matrix.common.dto.response;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.matrix.common.dto.model.Response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 对话响应
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 6737315355203850712L;

    private Long sessionId;
    private String id;
    private List<Response.Choice> choices;
    private Long created;
    private String model;
    private String object;
    private Response.Usage usage;
    private String system_fingerprint;
    private Response.Error error;

    /** ChatResponse操作 */
    public ChatResponse(Response response) {
        this.sessionId = response.getSessionId();
        this.id = response.getId();
        this.choices = response.getChoices();
        this.created = response.getCreated();
        this.model = response.getModel();
        this.object = response.getObject();
        this.usage = response.getUsage();
        this.system_fingerprint = response.getSystem_fingerprint();
        this.error = response.getError();
    }

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

}



