package com.webhookplatform.webhook.endpoint;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateWebhookEndpointRequest(
        @Size(max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 2048) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String url,
        EndpointStatus status) {

    public UpdateWebhookEndpointRequest {
        if (name != null) {
            name = name.trim();
        }
        if (url != null) {
            url = url.trim();
        }
    }

    @AssertTrue(message = "at least one of name, url, or status is required")
    public boolean isUpdatePresent() {
        return name != null || url != null || status != null;
    }
}
