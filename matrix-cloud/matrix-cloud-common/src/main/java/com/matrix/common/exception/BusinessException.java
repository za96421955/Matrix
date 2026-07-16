package com.matrix.common.exception;

import com.matrix.common.enums.ErrorCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常类
 */
@Getter
public class BusinessException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 5834723872123500012L;

    /**
     * HTTP 状态码
     */
    private final Integer httpStatus;
    
    /**
     * 业务错误码
     */
    private final String errorCode;
    
    /**
     * 构造方法
     * @param errorCode 错误码枚举
     * @param message 错误消息（可选，覆盖默认消息）
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message != null ? message : errorCode.getMessage());
        this.httpStatus = errorCode.getHttpStatus();
        this.errorCode = errorCode.getCode();
    }
    
    /**
     * 构造方法（使用默认消息）
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }
    
    /**
     * 构造方法（自定义 HTTP 状态码）
     * @param httpStatus HTTP 状态码
     * @param errorCode 业务错误码
     * @param message 错误消息
     */
    public BusinessException(Integer httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

}


