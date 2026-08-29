package com.webhookplatform.webhook.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWebhookEndpointRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2048) String url) {

    public CreateWebhookEndpointRequest {
        if (name != null) {
            name = name.trim();
        }
        if (url != null) {
            url = url.trim();
        }
    }
}
