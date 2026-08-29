package com.webhookplatform.webhook.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.webhookplatform.webhook.apikey.ApiKeyNotFoundException;
import com.webhookplatform.webhook.application.ApplicationNotFoundException;
import com.webhookplatform.webhook.application.ApplicationSlugConflictException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validationError() {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> malformedRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request could not be parsed.");
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    ResponseEntity<ApiErrorResponse> applicationNotFound(ApplicationNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ApiKeyNotFoundException.class)
    ResponseEntity<ApiErrorResponse> apiKeyNotFound(ApiKeyNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "API_KEY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ApplicationSlugConflictException.class)
    ResponseEntity<ApiErrorResponse> applicationSlugConflict(ApplicationSlugConflictException exception) {
        return error(HttpStatus.CONFLICT, "APPLICATION_SLUG_CONFLICT", exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
