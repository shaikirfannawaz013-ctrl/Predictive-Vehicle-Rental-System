package com.fleetiq.fleet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number")
    private String registrationNumber;

    private String name;

    @Enumerated(EnumType.STRING)
    private VehicleType type;

    private int seats;
    private String fuel;
    private String transmission;

    @Column(name = "base_rate")
    private BigDecimal baseRate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Enumerated(EnumType.STRING)
    private VehicleStatus status;

    @Column(name = "odometer_km")
    private int odometerKm;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "last_service_date")
    private LocalDate lastServiceDate;

    @Column(name = "last_service_odometer")
    private int lastServiceOdometer;

    @Column(name = "health_score")
    private Integer healthScore;

    @Version
    private long version;

    public boolean isElectric() {
        return "Electric".equalsIgnoreCase(fuel);
    }
}
