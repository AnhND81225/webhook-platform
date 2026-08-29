package com.webhookplatform.webhook.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateApplicationRequest(
        @Size(max = 120)
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        ApplicationStatus status) {

    public UpdateApplicationRequest {
        if (name != null) {
            name = name.trim();
        }
    }

    @AssertTrue(message = "at least one of name or status is required")
    public boolean isUpdatePresent() {
        return name != null || status != null;
    }
}
