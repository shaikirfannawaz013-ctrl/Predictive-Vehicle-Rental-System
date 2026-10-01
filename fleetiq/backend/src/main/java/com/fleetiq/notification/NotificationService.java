package com.fleetiq.notification;

import com.fleetiq.common.ApiException;
import com.fleetiq.common.EventPublisher;
import com.fleetiq.config.AppProperties;
import com.fleetiq.user.AuthUser;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores a notification, then publishes it to Kafka. Every backend instance consumes the topic
 * (unique consumer group each) and pushes it to the SSE connections it holds.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final EventPublisher events;
    private final String topic;

    public NotificationService(NotificationRepository repo, EventPublisher events, AppProperties props) {
        this.repo = repo;
        this.events = events;
        this.topic = props.topics().notifications();
    }

    @Transactional
    public void notifyUser(Long userId, NotificationType type, String message) {
        save(new Notification(userId, Audience.USER, type, message));
    }

    @Transactional
    public void notifyAdmins(NotificationType type, String message) {
        save(new Notification(null, Audience.ADMINS, type, message));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> visibleTo(AuthUser user) {
        return repo.findVisibleTo(user.id(), user.isAdmin(), PageRequest.of(0, 50)).stream()
                .map(NotificationResponse::from).toList();
    }

    @Transactional
    public void markRead(AuthUser user, Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> ApiException.notFound("Notification not found."));
        boolean visible = (n.getAudience() == Audience.USER && user.id().equals(n.getUserId()))
                || (n.getAudience() == Audience.ADMINS && user.isAdmin());
        if (!visible) {
            throw ApiException.notFound("Notification not found.");
        }
        n.setRead(true);
    }

    private void save(Notification n) {
        repo.save(n);
        events.publish(topic, n.getAudience().name(), NotificationResponse.from(n));
    }
}
