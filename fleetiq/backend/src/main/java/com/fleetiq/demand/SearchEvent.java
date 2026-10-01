package com.fleetiq.demand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Every vehicle search is stored; this log is the training data for the demand model. */
@Entity
@Table(name = "search_events")
@Getter
@Setter
@NoArgsConstructor
public class SearchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "zone_id")
    private String zoneId;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "created_at")
    private Instant createdAt;
}
