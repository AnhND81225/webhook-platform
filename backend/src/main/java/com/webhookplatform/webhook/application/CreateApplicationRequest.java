package com.webhookplatform.webhook.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 63)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @NotNull ApplicationEnvironment environment) {

    public CreateApplicationRequest {
        if (name != null) {
            name = name.trim();
        }
    }
}
