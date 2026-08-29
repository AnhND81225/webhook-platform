package com.webhookplatform.webhook.endpoint;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webhookplatform.webhook.security.CurrentUserService;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/endpoints")
public class WebhookEndpointController {

    private final WebhookEndpointService endpointService;
    private final CurrentUserService currentUserService;

    public WebhookEndpointController(WebhookEndpointService endpointService, CurrentUserService currentUserService) {
        this.endpointService = endpointService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<WebhookEndpointResponse> create(
            @PathVariable UUID applicationId,
            @Valid @RequestBody CreateWebhookEndpointRequest request) {
        WebhookEndpointResponse response = endpointService.create(
                applicationId, currentUserService.requireCurrentUser().id(), request);
        return ResponseEntity.created(URI.create("/api/v1/applications/" + applicationId + "/endpoints/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<WebhookEndpointResponse> list(@PathVariable UUID applicationId) {
        return endpointService.list(applicationId, currentUserService.requireCurrentUser().id());
    }

    @GetMapping("/{endpointId}")
    public WebhookEndpointResponse get(@PathVariable UUID applicationId, @PathVariable UUID endpointId) {
        return endpointService.get(applicationId, endpointId, currentUserService.requireCurrentUser().id());
    }

    @PatchMapping("/{endpointId}")
    public WebhookEndpointResponse update(
            @PathVariable UUID applicationId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody UpdateWebhookEndpointRequest request) {
        return endpointService.update(applicationId, endpointId, currentUserService.requireCurrentUser().id(), request);
    }
}
