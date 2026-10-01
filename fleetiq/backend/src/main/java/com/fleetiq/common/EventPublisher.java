package com.fleetiq.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes JSON events to Kafka. Inside a DB transaction the send waits until commit,
 * so consumers never see an event for data that was rolled back.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    public void publish(String topic, String key, Object payload) {
        publish(topic, key, payload, null);
    }

    /** @param fallback runs if Kafka can't accept the event (e.g. broker down); may be null */
    public void publish(String topic, String key, Object payload, Runnable fallback) {
        Runnable send = () -> {
            try {
                String json = mapper.writeValueAsString(payload);
                kafka.send(topic, key, json).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Kafka send to {} failed: {}", topic, ex.getMessage());
                    }
                });
            } catch (Exception e) {
                log.warn("Could not publish to {}: {}", topic, e.getMessage());
                if (fallback != null) {
                    fallback.run();
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
