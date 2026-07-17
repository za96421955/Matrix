package com.matrix.common.enums;

import lombok.Getter;

/**
 * 错误码定义（6 位数字）
 * 格式：XX XX XX
 *       |  |  |─ 具体错误
 *       |  |─ 模块标识
 *       |─ 错误类型 (00=通用，01=认证，02=授权，03=任务，04=设备，05=会话，06=Agent，07=三方服务，08=API Key)
 */
@Getter
public enum ErrorCode {
    
    // ========== 通用错误 (00) ==========
    SUCCESS(200, "000000", "操作成功"),
    SYSTEM_ERROR(500, "000001", "系统内部错误"),
    PARAM_ERROR(400, "000002", "参数错误"),
    AUTH_FAIL(401, "000003", "认证失败"),

    // ========== 认证错误 (01) ==========
    USER_NOT_FOUND(404, "010005", "用户不存在"),
    PASSWORD_ERROR(401, "010006", "密码错误"),
    AUTH_HEADER_INVALID(401, "010007", "无效的认证头信息"),
    
    // ========== 授权错误 (02) ==========
    RATE_LIMIT_EXCEEDED(429, "020005", "请求过于频繁"),
    
    // ========== 任务错误 (03) ==========
    TASK_NOT_FOUND(404, "030001", "任务不存在"),

    // ========== 设备错误 (04) ==========
    DEVICE_SERVICE_UNAVAILABLE(503, "040001", "设备服务不可用"),

    // ========== 会话错误 (05) ==========
    SESSION_USER_NOT_FOUND(404, "050001", "用户信息不存在"),

    // ========== Agent 错误 (06) ==========
    AGENT_SERVICE_UNAVAILABLE(503, "060001", "Agent 服务不可用"),
    AGENT_REQUEST_INVALID(400, "060002", "请求参数错误"),
    AGENT_NOT_FOUND(404, "060003", "Agent 不存在"),
    AGENT_DISABLED(403, "060004", "Agent 已禁用"),
    SKILL_REQUEST_INVALID(400, "060005", "请求参数错误"),
    SKILL_NOT_FOUND(404, "060006", "Skill 不存在"),
    MODEL_PARAM_INVALID(400, "060007", "请求参数错误"),
    MODEL_MESSAGE_EMPTY(400, "060008", "消息内容为空"),
    CHAT_PROCESS_ERROR(500, "060009", "对话处理失败"),
    APP_NOT_FOUND(404, "060010", "应用不存在"),
    APP_NOT_SUPPORT(400, "060011", "应用不支持"),
    HTTP_METHOD_NOT_SUPPORT(400, "060012", "HTTP 方法不支持"),
    IN_THE_CONVERSATION(500, "060013", "正在对话中"),
    CLIENT_NOT_FOUND(404, "060014", "没有在线的终端"),
    SKILL_DISABLED(404, "060015", "Skill 已禁用"),

    // ========== 三方服务错误 (07) ==========
    NOTIFICATION_SERVICE_UNAVAILABLE(503, "070001", "通知服务不可用"),

    // ========== API Key 错误 (08) ==========
    API_KEY_NOT_FOUND(404, "080001", "API Key 不存在"),
    API_KEY_DISABLED(403, "080002", "API Key 已禁用"),

    // ========== OAuth 错误 (10) ==========
    USER_ALREADY_EXISTS(400, "100001", "用户名已存在"),
    ;
    
    private final Integer httpStatus;
    private final String code;
    private final String message;
    
    ErrorCode(Integer httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

}
