package com.fleetiq.notification;

import com.fleetiq.user.AuthUser;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Open SSE connections held by this instance, keyed by user. */
@Component
public class SseRegistry {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Set<Long> adminIds = ConcurrentHashMap.newKeySet();

    public SseEmitter register(AuthUser user) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(user.id(), k -> new CopyOnWriteArraySet<>()).add(emitter);
        if (user.isAdmin()) {
            adminIds.add(user.id());
        }
        emitter.onCompletion(() -> remove(user.id(), emitter));
        emitter.onTimeout(emitter::complete);   // browser EventSource reconnects by itself
        emitter.onError(e -> remove(user.id(), emitter));
        send(user.id(), emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    public void deliver(NotificationResponse n) {
        if (n.audience() == Audience.ADMINS) {
            adminIds.forEach(id -> sendTo(id, n));
        } else if (n.userId() != null) {
            sendTo(n.userId(), n);
        }
    }

    /** Keeps proxies (nginx) from closing idle connections. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, set) -> set.forEach(e -> send(userId, e, SseEmitter.event().comment("ping"))));
    }

    private void sendTo(Long userId, NotificationResponse n) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null) {
            return;
        }
        set.forEach(e -> send(userId, e, SseEmitter.event()
                .name("notification")
                .id(String.valueOf(n.id()))
                .data(n, MediaType.APPLICATION_JSON)));
    }

    private void send(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(userId);
                adminIds.remove(userId);
            }
        }
    }
}
