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
import com.webhookplatform.webhook.endpoint.InvalidEndpointUrlException;
import com.webhookplatform.webhook.endpoint.WebhookEndpointNotFoundException;
import com.webhookplatform.webhook.endpoint.WebhookSigningSecretAlreadyProvisionedException;
import com.webhookplatform.webhook.event.InvalidWebhookEventException;
import com.webhookplatform.webhook.event.PayloadTooLargeException;
import com.webhookplatform.webhook.event.SourceEventConflictException;
import com.webhookplatform.webhook.subscription.WebhookSubscriptionConflictException;
import com.webhookplatform.webhook.subscription.WebhookSubscriptionNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validationError() {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        if (hasCause(exception, PayloadTooLargeException.class)) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Producer request exceeds the 1 MiB limit.");
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request could not be parsed.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
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

    @ExceptionHandler(WebhookEndpointNotFoundException.class)
    ResponseEntity<ApiErrorResponse> endpointNotFound(WebhookEndpointNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(WebhookSigningSecretAlreadyProvisionedException.class)
    ResponseEntity<ApiErrorResponse> signingSecretAlreadyProvisioned(WebhookSigningSecretAlreadyProvisionedException exception) {
        return error(HttpStatus.CONFLICT, "SIGNING_SECRET_ALREADY_PROVISIONED", exception.getMessage());
    }

    @ExceptionHandler(WebhookSubscriptionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> subscriptionNotFound(WebhookSubscriptionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(WebhookSubscriptionConflictException.class)
    ResponseEntity<ApiErrorResponse> subscriptionConflict(WebhookSubscriptionConflictException exception) {
        return error(HttpStatus.CONFLICT, "SUBSCRIPTION_ALREADY_EXISTS", exception.getMessage());
    }

    @ExceptionHandler(InvalidEndpointUrlException.class)
    ResponseEntity<ApiErrorResponse> invalidEndpointUrl(InvalidEndpointUrlException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ENDPOINT_URL", exception.getMessage());
    }

    @ExceptionHandler(InvalidWebhookEventException.class)
    ResponseEntity<ApiErrorResponse> invalidWebhookEvent(InvalidWebhookEventException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_EVENT_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(SourceEventConflictException.class)
    ResponseEntity<ApiErrorResponse> sourceEventConflict(SourceEventConflictException exception) {
        return error(HttpStatus.CONFLICT, "SOURCE_EVENT_ID_CONFLICT", exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> expectedCause) {
        Throwable current = exception;
        while (current != null) {
            if (expectedCause.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
