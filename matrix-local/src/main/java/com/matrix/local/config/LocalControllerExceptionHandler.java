//package com.matrix.local.config;
//
//import com.matrix.common.enums.ErrorCode;
//import com.matrix.common.exception.BusinessException;
//import com.matrix.common.response.CommonResponse;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.validation.BindException;
//import org.springframework.validation.FieldError;
//import org.springframework.web.bind.MethodArgumentNotValidException;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//import org.springframework.web.bind.annotation.RestControllerAdvice;
//
//import java.util.stream.Collectors;
//
///**
// * Local 全局异常处理器
// * <p> 覆盖 matrix-cloud-service 的 ControllerExceptionHandler，处理所有控制器异常 </p>
// *
// * @author 陈晨
// */
//@Slf4j
//@RestControllerAdvice
//public class LocalControllerExceptionHandler {
//
//    /**
//     * 处理业务异常
//     */
//    @ExceptionHandler(BusinessException.class)
//    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException e) {
//        log.error("[业务异常] errorCode={}, message={}", e.getErrorCode(), e.getMessage());
//        CommonResponse<Void> response = CommonResponse.build(
//                e.getHttpStatus(),
//                e.getErrorCode(),
//                e.getMessage(),
//                null);
//        return ResponseEntity.status(e.getHttpStatus()).body(response);
//    }
//
//    /**
//     * 处理参数校验异常（@Valid）
//     */
//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public ResponseEntity<CommonResponse<Void>> handleMethodArgumentNotValidException(
//            MethodArgumentNotValidException e) {
//        String message = e.getBindingResult().getFieldErrors().stream()
//                .map(FieldError::getDefaultMessage)
//                .collect(Collectors.joining(", "));
//        log.error("[参数校验异常] message={}", message);
//        CommonResponse<Void> response = CommonResponse.error(ErrorCode.PARAM_ERROR, message);
//        return ResponseEntity.badRequest().body(response);
//    }
//
//    /**
//     * 处理绑定异常
//     */
//    @ExceptionHandler(BindException.class)
//    public ResponseEntity<CommonResponse<Void>> handleBindException(BindException e) {
//        String message = e.getBindingResult().getFieldErrors().stream()
//                .map(FieldError::getDefaultMessage)
//                .collect(Collectors.joining(", "));
//        log.error("[绑定异常] message={}", message);
//        CommonResponse<Void> response = CommonResponse.error(ErrorCode.PARAM_ERROR, message);
//        return ResponseEntity.badRequest().body(response);
//    }
//
//    /**
//     * 处理系统异常
//     */
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<CommonResponse<Void>> handleException(Exception e) {
//        log.error("[系统异常] message={}", e.getMessage(), e);
//        CommonResponse<Void> response = CommonResponse.error(ErrorCode.SYSTEM_ERROR);
//        return ResponseEntity.internalServerError().body(response);
//    }
//
//}
