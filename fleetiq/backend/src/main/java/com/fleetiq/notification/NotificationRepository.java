package com.fleetiq.notification;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n from Notification n
            where (n.audience = com.fleetiq.notification.Audience.USER and n.userId = :userId)
               or (:admin = true and n.audience = com.fleetiq.notification.Audience.ADMINS)
            order by n.createdAt desc
            """)
    List<Notification> findVisibleTo(@Param("userId") Long userId, @Param("admin") boolean admin, Pageable page);
}
