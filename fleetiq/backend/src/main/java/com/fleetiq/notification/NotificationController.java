package com.fleetiq.notification;

import com.fleetiq.user.AuthUser;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final SseRegistry registry;

    public NotificationController(NotificationService notifications, SseRegistry registry) {
        this.notifications = notifications;
        this.registry = registry;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AuthUser user) {
        return notifications.visibleTo(user);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        notifications.markRead(user, id);
        return ResponseEntity.noContent().build();
    }

    /** Live stream. The frontend connects with EventSource('/api/notifications/stream?token=<jwt>'). */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser user) {
        return registry.register(user);
    }
}
