package com.webhookplatform.webhook.endpoint;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webhookplatform.webhook.application.Application;
import com.webhookplatform.webhook.application.ApplicationService;

@Service
public class WebhookEndpointService {

    private final WebhookEndpointRepository endpointRepository;
    private final ApplicationService applicationService;
    private final EndpointUrlValidator endpointUrlValidator;
    private final Clock clock;

    public WebhookEndpointService(
            WebhookEndpointRepository endpointRepository,
            ApplicationService applicationService,
            EndpointUrlValidator endpointUrlValidator,
            Clock clock) {
        this.endpointRepository = endpointRepository;
        this.applicationService = applicationService;
        this.endpointUrlValidator = endpointUrlValidator;
        this.clock = clock;
    }

    @Transactional
    public WebhookEndpointResponse create(
            UUID applicationId, UUID ownerUserId, CreateWebhookEndpointRequest request) {
        Application application = applicationService.requireOwnedApplication(applicationId, ownerUserId);
        endpointUrlValidator.validate(request.url());
        WebhookEndpoint endpoint = WebhookEndpoint.create(application, request.name(), request.url(), clock.instant());
        return WebhookEndpointResponse.from(endpointRepository.save(endpoint));
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> list(UUID applicationId, UUID ownerUserId) {
        applicationService.requireOwnedApplication(applicationId, ownerUserId);
        return endpointRepository.findAllByApplicationIdOrderByCreatedAtDescIdDesc(applicationId)
                .stream()
                .map(WebhookEndpointResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse get(UUID applicationId, UUID endpointId, UUID ownerUserId) {
        return WebhookEndpointResponse.from(requireOwnedEndpoint(applicationId, endpointId, ownerUserId));
    }

    @Transactional
    public WebhookEndpointResponse update(
            UUID applicationId, UUID endpointId, UUID ownerUserId, UpdateWebhookEndpointRequest request) {
        WebhookEndpoint endpoint = requireOwnedEndpoint(applicationId, endpointId, ownerUserId);
        if (request.url() != null) {
            endpointUrlValidator.validate(request.url());
        }
        endpoint.update(request.name(), request.url(), request.status(), clock.instant());
        return WebhookEndpointResponse.from(endpoint);
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint requireOwnedEndpoint(UUID applicationId, UUID endpointId, UUID ownerUserId) {
        applicationService.requireOwnedApplication(applicationId, ownerUserId);
        return endpointRepository.findByIdAndApplicationIdAndApplicationOwnerUserId(endpointId, applicationId, ownerUserId)
                .orElseThrow(WebhookEndpointNotFoundException::new);
    }
}
