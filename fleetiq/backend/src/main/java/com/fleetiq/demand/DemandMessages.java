package com.fleetiq.demand;

import java.time.Instant;

public final class DemandMessages {
    private DemandMessages() {}

    /** Published to Kafka topic app.topics.vehicle-searches on every search. */
    public record SearchEventMessage(Long userId, String zoneId, String vehicleType,
                                     Instant startTime, Instant endTime, Instant searchedAt) {}

    /** Published to app.topics.booking-events whenever a booking changes state. */
    public record BookingEventMessage(String event, Long bookingId, Long vehicleId, String zoneId,
                                      String vehicleType, Instant at) {}

    /** One zone's live demand picture, cached in Redis for 30 seconds. */
    public record ZoneDemand(String id, String name, int x, int y, double latitude, double longitude,
                             double demand, int supply, int searches, int upcomingBookings, double multiplier) {}
}
