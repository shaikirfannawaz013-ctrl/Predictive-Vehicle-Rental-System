package com.fleetiq.notification;

import java.time.Instant;

/** Sent over REST and SSE. userId/audience are only used for routing and are not shown in the UI. */
public record NotificationResponse(Long id, Long userId, Audience audience, NotificationType type, String message,
                                   Instant createdAt, boolean read) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getAudience(), n.getType(), n.getMessage(),
                n.getCreatedAt(), n.isRead());
    }
}
