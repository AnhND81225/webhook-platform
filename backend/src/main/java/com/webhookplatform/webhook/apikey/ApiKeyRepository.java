package com.webhookplatform.webhook.apikey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findAllByApplicationIdOrderByCreatedAtDescIdDesc(UUID applicationId);

    Optional<ApiKey> findByIdAndApplicationOwnerUserId(UUID id, UUID ownerUserId);
}
