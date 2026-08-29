package com.webhookplatform.webhook.apikey;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webhookplatform.webhook.security.CurrentUserService;

@RestController
@RequestMapping("/api/v1")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final CurrentUserService currentUserService;

    public ApiKeyController(ApiKeyService apiKeyService, CurrentUserService currentUserService) {
        this.apiKeyService = apiKeyService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/applications/{applicationId}/api-keys")
    public ResponseEntity<CreatedApiKeyResponse> create(
            @PathVariable UUID applicationId,
            @Valid @RequestBody CreateApiKeyRequest request) {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(apiKeyService.create(
                        applicationId,
                        currentUserService.requireCurrentUser().id(),
                        request));
    }

    @GetMapping("/applications/{applicationId}/api-keys")
    public List<ApiKeyMetadataResponse> list(@PathVariable UUID applicationId) {
        return apiKeyService.list(applicationId, currentUserService.requireCurrentUser().id());
    }

    @PostMapping("/api-keys/{apiKeyId}/revoke")
    public ApiKeyMetadataResponse revoke(@PathVariable UUID apiKeyId) {
        return apiKeyService.revoke(apiKeyId, currentUserService.requireCurrentUser().id());
    }
}
