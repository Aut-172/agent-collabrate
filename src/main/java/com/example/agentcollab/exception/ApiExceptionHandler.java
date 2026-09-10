package com.example.agentcollab.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handle(ApiException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.putAll(ex.getDetails());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<Map<String, Object>> handleValidation(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", Instant.now(), "code", "VALIDATION_ERROR", "message", "请求参数校验失败"));
    }
}
