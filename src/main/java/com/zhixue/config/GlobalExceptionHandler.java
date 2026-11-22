package com.zhixue.config;

import com.zhixue.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 所有未捕获异常 → 打完整堆栈，返回友好 JSON（而不是 Whitelabel Error Page）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAll(Exception e) {
        log.error("========== 未捕获异常 ==========", e);
        // 不把原始异常信息回传客户端（可能含 SQL/内部路径/第三方细节），完整堆栈只写服务端日志
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("服务器内部错误，请稍后重试或联系管理员"));
    }

    // 认证相关 → 401
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<?> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("未登录或权限不足"));
    }

    // 请求体缺字段导致的 NPE（如手工调 API 少传 courseId/points）→ 400 业务提示而非 500
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<?> handleNpe(NullPointerException e) {
        log.warn("请求参数不完整（NPE）：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("请求参数不完整，请检查必填字段"));
    }

    // 参数绑定异常 → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArg(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("参数错误：" + e.getMessage()));
    }

    // 路径/静态资源不存在 → 404（必须优先于上面的 Exception 兜底，否则会被当成 500）
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("请求的资源不存在：" + e.getResourcePath()));
    }
}
