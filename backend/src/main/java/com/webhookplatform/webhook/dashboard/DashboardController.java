package com.webhookplatform.webhook.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.webhookplatform.webhook.security.CurrentUserService;
import com.webhookplatform.webhook.event.CreateWebhookEventRequest;
import com.webhookplatform.webhook.event.WebhookEventResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}")
public class DashboardController {
    private final DashboardService dashboard;
    private final TestEventService testEvents;
    private final CurrentUserService currentUser;
    DashboardController(DashboardService dashboard, TestEventService testEvents, CurrentUserService currentUser) { this.dashboard = dashboard; this.testEvents = testEvents; this.currentUser = currentUser; }
    @PostMapping("/test-events") public ResponseEntity<WebhookEventResponse> testEvent(@PathVariable UUID applicationId, @Valid @RequestBody CreateWebhookEventRequest request) {
        var result = testEvents.send(applicationId, currentUser.requireCurrentUser().id(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.event());
    }

    @GetMapping("/dashboard/summary") public DashboardSummaryResponse summary(@PathVariable UUID applicationId) {
        return dashboard.summary(applicationId, currentUser.requireCurrentUser().id());
    }
    @GetMapping("/events") public CursorPage<EventListItem> events(@PathVariable UUID applicationId,
            @RequestParam(required=false) String cursor, @RequestParam(required=false) Integer size,
            @RequestParam(required=false) String eventType, @RequestParam(required=false) String sourceEventId,
            @RequestParam(required=false) Instant createdFrom, @RequestParam(required=false) Instant createdTo) {
        return dashboard.events(applicationId, currentUser.requireCurrentUser().id(), cursor, size, eventType, sourceEventId, createdFrom, createdTo);
    }
    @GetMapping("/events/{eventId}") public EventDetailResponse event(@PathVariable UUID applicationId, @PathVariable UUID eventId) {
        return dashboard.event(applicationId, eventId, currentUser.requireCurrentUser().id());
    }
    @GetMapping("/deliveries") public CursorPage<DeliveryListItem> deliveries(@PathVariable UUID applicationId,
            @RequestParam(required=false) String cursor, @RequestParam(required=false) Integer size,
            @RequestParam(required=false) String status, @RequestParam(required=false) UUID endpointId,
            @RequestParam(required=false) String eventType, @RequestParam(required=false) Instant createdFrom, @RequestParam(required=false) Instant createdTo) {
        return dashboard.deliveries(applicationId, currentUser.requireCurrentUser().id(), cursor, size, status, endpointId, eventType, createdFrom, createdTo);
    }
    @GetMapping("/deliveries/{deliveryId}") public DeliveryDetailResponse delivery(@PathVariable UUID applicationId, @PathVariable UUID deliveryId) {
        return dashboard.delivery(applicationId, deliveryId, currentUser.requireCurrentUser().id());
    }
    @GetMapping("/deliveries/{deliveryId}/attempts") public List<AttemptItem> attempts(@PathVariable UUID applicationId, @PathVariable UUID deliveryId) {
        return dashboard.attempts(applicationId, deliveryId, currentUser.requireCurrentUser().id());
    }
}

record DashboardSummaryResponse(EventCounts events, DeliveryCounts deliveries, long recentFailures) {}
record EventCounts(long total, long last24Hours) {}
record DeliveryCounts(long pending, long processing, long retryScheduled, long delivered, long failed) {}
record CursorPage<T>(List<T> items, String nextCursor) {}
record EventListItem(UUID id, String sourceEventId, String eventType, Instant createdAt, long deliveryCount, long deliveredCount, long failedCount, long retryScheduledCount) {}
record EventDetailResponse(UUID id, String sourceEventId, String eventType, Instant createdAt, com.fasterxml.jackson.databind.JsonNode payload, long deliveryCount, long deliveredCount, long failedCount, long retryScheduledCount) {}
record DeliveryListItem(UUID id, UUID eventId, String eventType, UUID endpointId, String endpointName, String status, Instant nextRetryAt, long attemptCount, AttemptSummary lastAttempt, Instant createdAt, Instant updatedAt) {}
record DeliveryDetailResponse(UUID id, String status, EventReference event, EndpointReference endpoint, String targetUrl, Instant nextRetryAt, Instant createdAt, Instant updatedAt) {}
record EventReference(UUID id, String sourceEventId, String eventType, Instant createdAt) {}
record EndpointReference(UUID id, String name, String url) {}
record AttemptSummary(int attemptNumber, String status, Integer httpStatusCode, String errorCode) {}
record AttemptItem(UUID id, int attemptNumber, String status, Instant startedAt, Instant completedAt, Long durationMs, Integer httpStatusCode, String errorCode) {}
