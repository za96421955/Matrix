package com.matrix.common.dto.request;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.dto.model.Request;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.List;

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

    /** 终端ID */
    private String clientId;
    /** 工作目录 */
    private String itemPath;

    /** 引用的会话ID列表，消息构建时优先加载这些会话的全量消息作为上下文 */
    private List<Long> referencedSessionIds;

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    /** 克隆对象副本 */
    public PatternRequest clone() {
        return JSON.parseObject(JSON.toJSONString(this), PatternRequest.class);
    }

}
