package com.webhookplatform.webhook.apikey;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webhookplatform.webhook.application.ApplicationStatus;
import com.webhookplatform.webhook.security.ProducerPrincipal;

@Service
public class ApiKeyAuthenticationService {

    private final ApiKeyRepository apiKeyRepository;
    private final Clock clock;

    public ApiKeyAuthenticationService(ApiKeyRepository apiKeyRepository, Clock clock) {
        this.apiKeyRepository = apiKeyRepository;
        this.clock = clock;
    }

    @Transactional
    public ProducerPrincipal authenticate(String rawApiKey) {
        ApiKey apiKey = apiKeyRepository.findByKeyHash(ApiKeyGenerator.sha256(rawApiKey))
                .orElseThrow(InvalidApiKeyException::new);

        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE
                || apiKey.getApplication().getStatus() != ApplicationStatus.ACTIVE) {
            throw new InvalidApiKeyException();
        }

        apiKey.markUsed(clock.instant());
        return new ProducerPrincipal(apiKey.getId(), apiKey.getApplication().getId());
    }
}
