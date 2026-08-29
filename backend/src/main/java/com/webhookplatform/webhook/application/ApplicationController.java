package com.webhookplatform.webhook.application;

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
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final CurrentUserService currentUserService;

    public ApplicationController(ApplicationService applicationService, CurrentUserService currentUserService) {
        this.applicationService = applicationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse response = applicationService.create(
                currentUserService.requireCurrentUser().id(), request);
        return ResponseEntity.created(URI.create("/api/v1/applications/" + response.id())).body(response);
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return applicationService.list(currentUserService.requireCurrentUser().id());
    }

    @GetMapping("/{applicationId}")
    public ApplicationResponse get(@PathVariable UUID applicationId) {
        return applicationService.get(applicationId, currentUserService.requireCurrentUser().id());
    }

    @PatchMapping("/{applicationId}")
    public ApplicationResponse update(
            @PathVariable UUID applicationId,
            @Valid @RequestBody UpdateApplicationRequest request) {
        return applicationService.update(
                applicationId,
                currentUserService.requireCurrentUser().id(),
                request);
    }
}
