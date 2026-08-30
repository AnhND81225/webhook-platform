package com.webhookplatform.webhook.delivery;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import com.webhookplatform.webhook.event.WebhookEvent;
import com.webhookplatform.webhook.subscription.WebhookSubscriptionRepository;

@Service
public class WebhookDeliveryService {
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final Clock clock;
    public WebhookDeliveryService(WebhookSubscriptionRepository subscriptions, WebhookDeliveryRepository deliveries, Clock clock) {
        this.subscriptions = subscriptions; this.deliveries = deliveries; this.clock = clock;
    }
    public void createFor(WebhookEvent event) {
        List<WebhookSubscriptionRepository.ActiveEndpointTarget> targets = subscriptions.findActiveEndpointTargets(event.getApplication().getId(), event.getEventType());
        deliveries.saveAll(targets.stream().map(target -> WebhookDelivery.create(event, target.endpoint(), target.url(), clock.instant())).toList());
    }
}
