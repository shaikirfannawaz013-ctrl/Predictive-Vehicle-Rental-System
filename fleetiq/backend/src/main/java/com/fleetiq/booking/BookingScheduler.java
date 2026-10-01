package com.fleetiq.booking;

import com.fleetiq.fleet.VehicleStatus;
import com.fleetiq.notification.NotificationService;
import com.fleetiq.notification.NotificationType;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background jobs for the booking lifecycle.
 * With several backend instances, add ShedLock so each job runs on one instance only.
 */
@Component
public class BookingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduler.class);
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final NotificationService notifications;

    public BookingScheduler(BookingRepository bookings, BookingService bookingService,
                            NotificationService notifications) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.notifications = notifications;
    }

    /** Unpaid holds past their expiry release the vehicle. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    @Transactional
    public void expireHolds() {
        for (Booking b : bookings.findExpiredHolds(BookingStatus.PENDING_PAYMENT, Instant.now())) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setHoldExpiresAt(null);
            notifications.notifyUser(b.getCustomer().getId(), NotificationType.BOOKING,
                    "Your hold on " + b.getVehicle().getName() + " expired before payment. Search again to rebook.");
            bookingService.publish("HOLD_EXPIRED", b);
        }
    }

    /** Confirmed bookings become active at pickup time (keyless handover). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void startDueBookings() {
        for (Booking b : bookings.findByStatusStartedBy(BookingStatus.CONFIRMED, Instant.now())) {
            b.setStatus(BookingStatus.ACTIVE);
            b.getVehicle().setStatus(VehicleStatus.RENTED);
            bookingService.publish("BOOKING_STARTED", b);
        }
    }

    /** Automated late-return detection: alert once per booking after the grace period. */
    @Scheduled(fixedDelayString = "${app.schedule.late-check-ms:300000}", initialDelay = 40_000)
    @Transactional
    public void detectLateReturns() {
        Instant now = Instant.now();
        for (Booking b : bookings.findByStatusEndingBefore(BookingStatus.ACTIVE, now)) {
            if (b.isLateNotified() || bookingService.billableLateHours(b.getEndTime(), now) == 0) {
                continue;
            }
            b.setLateNotified(true);
            long hours = Math.max(1, Duration.between(b.getEndTime(), now).toHours());
            notifications.notifyAdmins(NotificationType.LATE_RETURN, "Booking #" + b.getId() + " ("
                    + b.getVehicle().getName() + ", " + b.getCustomer().getName() + ") is " + hours + " h overdue.");
            notifications.notifyUser(b.getCustomer().getId(), NotificationType.LATE_RETURN,
                    "Your booking for " + b.getVehicle().getName() + " has ended. Return it as soon as you can: late fees apply per hour.");
            log.info("Late return flagged for booking {}", b.getId());
        }
    }
}
