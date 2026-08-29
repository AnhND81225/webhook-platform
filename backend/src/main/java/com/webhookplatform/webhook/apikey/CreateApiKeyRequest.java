package com.webhookplatform.webhook.apikey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(@NotBlank @Size(max = 120) String name) {

    public CreateApiKeyRequest {
        if (name != null) {
            name = name.trim();
        }
    }
}
