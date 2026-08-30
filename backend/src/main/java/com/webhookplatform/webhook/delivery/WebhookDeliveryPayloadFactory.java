package com.webhookplatform.webhook.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

@Component
class WebhookDeliveryPayloadFactory {

    private final ObjectMapper objectMapper;

    WebhookDeliveryPayloadFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String create(ClaimedDelivery delivery) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("id", delivery.eventId().toString());
            body.put("sourceEventId", delivery.sourceEventId());
            body.put("eventType", delivery.eventType());
            body.put("createdAt", delivery.eventCreatedAt().toString());
            JsonNode payload = objectMapper.readTree(delivery.payloadJson());
            body.set("payload", payload);
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted webhook event payload could not be serialized.", exception);
        }
    }
}
