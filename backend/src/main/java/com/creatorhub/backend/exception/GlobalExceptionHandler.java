package com.creatorhub.backend.exception;

import com.creatorhub.backend.dto.ApiError;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(WorkNotFoundException.class)
    public ResponseEntity<ApiError> workNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("WORK_NOT_FOUND", "作品不存在"));
    }

    @ExceptionHandler(WorkForbiddenException.class)
    public ResponseEntity<ApiError> workForbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("WORK_FORBIDDEN", "只有作者可以编辑作品"));
    }

    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<ApiError> usernameTaken(UsernameTakenException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("USERNAME_TAKEN", "用户名已被使用"));
    }

    @ExceptionHandler(org.springframework.security.core.userdetails.UsernameNotFoundException.class)
    public ResponseEntity<ApiError> unauthenticated(Exception exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("UNAUTHENTICATED", "请重新登录"));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> invalidRequest(InvalidRequestException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> databaseFailure(DataAccessException exception) {
        // 不将 SQL、连接信息或驱动异常链写入响应或日志。
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("DATABASE_UNAVAILABLE", "数据库暂时不可用，请稍后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpectedFailure(Exception exception) {
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // 保留 Spring 判断的 HTTP 状态与协议头，但不回显用户输入及异常详情。
        String code = status.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR";
        String message = status.is4xxClientError() ? "请求参数、路径或方法不正确" : "服务暂时不可用";
        return super.handleExceptionInternal(exception, new ApiError(code, message), headers, status, request);
    }
}
