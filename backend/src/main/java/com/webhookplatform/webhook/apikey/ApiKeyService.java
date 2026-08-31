package com.webhookplatform.webhook.apikey;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webhookplatform.webhook.application.Application;
import com.webhookplatform.webhook.application.ApplicationService;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApplicationService applicationService;
    private final ApiKeyGenerator apiKeyGenerator;
    private final Clock clock;

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            ApplicationService applicationService,
            ApiKeyGenerator apiKeyGenerator,
            Clock clock) {
        this.apiKeyRepository = apiKeyRepository;
        this.applicationService = applicationService;
        this.apiKeyGenerator = apiKeyGenerator;
        this.clock = clock;
    }

    @Transactional
    public CreatedApiKeyResponse create(UUID applicationId, UUID ownerUserId, CreateApiKeyRequest request) {
        Application application = applicationService.requireOwnedApplication(applicationId, ownerUserId);
        ApiKeyGenerator.GeneratedApiKey generated = apiKeyGenerator.generate(application.getEnvironment());
        ApiKey apiKey = ApiKey.create(
                application,
                request.name(),
                generated.prefix(),
                generated.hash(),
                clock.instant());
        ApiKey persisted = apiKeyRepository.saveAndFlush(apiKey);
        return CreatedApiKeyResponse.from(persisted, generated.rawKey());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyMetadataResponse> list(UUID applicationId, UUID ownerUserId) {
        applicationService.requireOwnedApplication(applicationId, ownerUserId);
        return apiKeyRepository.findAllByApplicationIdOrderByCreatedAtDescIdDesc(applicationId)
                .stream()
                .map(ApiKeyMetadataResponse::from)
                .toList();
    }

    @Transactional
    public ApiKeyMetadataResponse revoke(UUID apiKeyId, UUID ownerUserId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndApplicationOwnerUserId(apiKeyId, ownerUserId)
                .orElseThrow(ApiKeyNotFoundException::new);
        apiKey.revoke(clock.instant().truncatedTo(ChronoUnit.MICROS));
        return ApiKeyMetadataResponse.from(apiKey);
    }
}
