package com.fleetiq.booking;

import java.util.List;

public enum BookingStatus {
    PENDING_PAYMENT, CONFIRMED, ACTIVE, COMPLETED, CANCELLED;

    /** Statuses that occupy the vehicle (PENDING_PAYMENT only while its hold is valid). */
    public static final List<BookingStatus> OCCUPYING = List.of(CONFIRMED, ACTIVE);

    /** Statuses that count as real usage for analytics and risk. */
    public static final List<BookingStatus> REALISED = List.of(CONFIRMED, ACTIVE, COMPLETED);
}
