package com.fleetiq.demand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetiq.demand.DemandMessages.BookingEventMessage;
import com.fleetiq.demand.DemandMessages.SearchEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes search and booking events and keeps the live demand picture current. */
@Component
public class DemandEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(DemandEventsConsumer.class);
    private final DemandService demand;
    private final SearchEventRepository searchLog;
    private final ObjectMapper mapper;

    public DemandEventsConsumer(DemandService demand, SearchEventRepository searchLog, ObjectMapper mapper) {
        this.demand = demand;
        this.searchLog = searchLog;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.topics.vehicle-searches}", groupId = "fleetiq-demand")
    public void onSearch(String json) {
        try {
            SearchEventMessage m = mapper.readValue(json, SearchEventMessage.class);
            demand.recordSearch(m.zoneId(), m.vehicleType(), m.searchedAt());

            SearchEvent e = new SearchEvent();
            e.setUserId(m.userId());
            e.setZoneId(m.zoneId());
            e.setVehicleType(m.vehicleType());
            e.setStartTime(m.startTime());
            e.setEndTime(m.endTime());
            e.setCreatedAt(m.searchedAt());
            searchLog.save(e);
        } catch (Exception ex) {
            log.warn("Skipping bad search event: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "${app.topics.booking-events}", groupId = "fleetiq-demand")
    public void onBookingEvent(String json) {
        try {
            BookingEventMessage m = mapper.readValue(json, BookingEventMessage.class);
            log.debug("Booking event {} for booking {}", m.event(), m.bookingId());
            demand.invalidate(); // supply or upcoming bookings changed: recompute on next read
        } catch (Exception ex) {
            log.warn("Skipping bad booking event: {}", ex.getMessage());
        }
    }
}
