package com.webhookplatform.webhook.event;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByApplicationIdAndSourceEventId(UUID applicationId, String sourceEventId);

    @Query(value = """
            select exists (
                select 1
                from webhook_events
                where application_id = :applicationId
                  and source_event_id = :sourceEventId
                  and event_type = :eventType
                  and payload = cast(:payload as jsonb)
            )
            """, nativeQuery = true)
    boolean existsExactEvent(
            @Param("applicationId") UUID applicationId,
            @Param("sourceEventId") String sourceEventId,
            @Param("eventType") String eventType,
            @Param("payload") String payload);
}
