package com.example.situation.config;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("API validation failed: {}", ex.getMessage());
        Map<String, String> body = new HashMap<>();
        body.put("error", "Validation failed");
        body.put("message", "The request payload is invalid.");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("API constraint violation: {}", ex.getMessage());
        Map<String, String> body = new HashMap<>();
        body.put("error", "Validation failed");
        body.put("message", "The request parameters are invalid.");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("API request rejected: {}", ex.getMessage());
        Map<String, String> body = new HashMap<>();
        body.put("error", "Request rejected");
        body.put("message", "The request could not be processed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        if (status.is5xxServerError()) {
            log.error("API server error: {}", ex.getReason(), ex);
        } else {
            log.warn("API response status {}: {}", status.value(), ex.getReason());
        }

        Map<String, String> body = new HashMap<>();
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getReason() == null || ex.getReason().isBlank()
            ? "The request could not be completed."
            : ex.getReason());
        return ResponseEntity.status(status).body(body);
    }
}
