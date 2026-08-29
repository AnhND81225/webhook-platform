package com.webhookplatform.webhook.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findAllByOwnerUserIdOrderByCreatedAtDescIdDesc(UUID ownerUserId);

    Optional<Application> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
