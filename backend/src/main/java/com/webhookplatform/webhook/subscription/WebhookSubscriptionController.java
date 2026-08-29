package com.webhookplatform.webhook.subscription;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webhookplatform.webhook.security.CurrentUserService;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/endpoints/{endpointId}/subscriptions")
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService subscriptionService;
    private final CurrentUserService currentUserService;

    public WebhookSubscriptionController(
            WebhookSubscriptionService subscriptionService,
            CurrentUserService currentUserService) {
        this.subscriptionService = subscriptionService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<WebhookSubscriptionResponse> create(
            @PathVariable UUID applicationId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody CreateWebhookSubscriptionRequest request) {
        WebhookSubscriptionResponse response = subscriptionService.create(
                applicationId, endpointId, currentUserService.requireCurrentUser().id(), request);
        return ResponseEntity.created(URI.create(
                "/api/v1/applications/" + applicationId + "/endpoints/" + endpointId + "/subscriptions/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<WebhookSubscriptionResponse> list(
            @PathVariable UUID applicationId, @PathVariable UUID endpointId) {
        return subscriptionService.list(applicationId, endpointId, currentUserService.requireCurrentUser().id());
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID applicationId,
            @PathVariable UUID endpointId,
            @PathVariable UUID subscriptionId) {
        subscriptionService.delete(applicationId, endpointId, subscriptionId, currentUserService.requireCurrentUser().id());
        return ResponseEntity.noContent().build();
    }
}
