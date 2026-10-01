package com.fleetiq.allocation;

import com.fleetiq.fleet.Vehicle;
import com.fleetiq.fleet.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "allocation_moves")
@Getter
@Setter
@NoArgsConstructor
public class AllocationMove {

    public static final String SUGGESTED = "SUGGESTED";
    public static final String DISPATCHED = "DISPATCHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_zone_id")
    private Zone fromZone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_zone_id")
    private Zone toZone;

    @Column(name = "expected_gain")
    private BigDecimal expectedGain;

    private String reason;
    private String status;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
