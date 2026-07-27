package com.matrix.common.response;

import com.matrix.common.enums.ErrorCode;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应格式
 * 格式：{code: 200/500/..., message: xxx, data: xxx}
 */
@Data
public class CommonResponse<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1149329100170045587L;

    /**
     * 状态码
     */
    private Integer status;

    /**
     * 错误码
     */
    private String code;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 数据
     */
    private T data;
    
    /** CommonResponse操作 */
    public CommonResponse() {
    }
    
    /** CommonResponse操作 */
    public CommonResponse(Integer status, String code, String message, T data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 构建对象或命令 */
    public static <T> CommonResponse<T> build(int status, String code, String message, T data) {
        return new CommonResponse<>(status, code, message, data);
    }

    /** 构建对象或命令 */
    public static <T> CommonResponse<T> build(ErrorCode errorCode, String message, T data) {
        return new CommonResponse<>(errorCode.getHttpStatus(), errorCode.getCode(), message, data);
    }

    /** 构建对象或命令 */
    public static <T> CommonResponse<T> build(ErrorCode errorCode, T data) {
        return new CommonResponse<>(errorCode.getHttpStatus(), errorCode.getCode(), errorCode.getMessage(), data);
    }

    /** success操作 */
    public static <T> CommonResponse<T> success(String message, T data) {
        return build(ErrorCode.SUCCESS, message, data);
    }
    
    /** success操作 */
    public static <T> CommonResponse<T> success(T data) {
        return build(ErrorCode.SUCCESS, data);
    }
    
    /** success操作 */
    public static <T> CommonResponse<T> success() {
        return build(ErrorCode.SUCCESS, null);
    }

    /** error操作 */
    public static <T> CommonResponse<T> error(ErrorCode errorCode, String message) {
        return build(errorCode, message, null);
    }

    /** error操作 */
    public static <T> CommonResponse<T> error(ErrorCode errorCode) {
        return build(errorCode, null);
    }
    
    /** error操作 */
    public static <T> CommonResponse<T> error(String message) {
        return error(ErrorCode.SYSTEM_ERROR, message);
    }

}


