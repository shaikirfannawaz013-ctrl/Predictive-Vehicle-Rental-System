package com.fleetiq.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Each instance joins its own consumer group (random instance id), so every instance sees every
 * notification and forwards it to whichever users are connected to it. Starts from "latest" so a
 * restart doesn't replay old alerts.
 */
@Component
public class NotificationSseListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseListener.class);
    private final SseRegistry registry;
    private final ObjectMapper mapper;

    public NotificationSseListener(SseRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.topics.notifications}", groupId = "fleetiq-sse-${app.instance-id}",
            properties = "auto.offset.reset=latest")
    public void onNotification(String json) {
        try {
            registry.deliver(mapper.readValue(json, NotificationResponse.class));
        } catch (Exception e) {
            log.warn("Skipping bad notification event: {}", e.getMessage());
        }
    }
}
