package com.webhookplatform.webhook.apikey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findAllByApplicationIdOrderByCreatedAtDescIdDesc(UUID applicationId);

    Optional<ApiKey> findByIdAndApplicationOwnerUserId(UUID id, UUID ownerUserId);

    @Query("select apiKey from ApiKey apiKey join fetch apiKey.application where apiKey.keyHash = :keyHash")
    Optional<ApiKey> findByKeyHash(@Param("keyHash") String keyHash);
}
