package com.fleetiq.fleet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A pickup area (e.g. Tirupati Central). Demand and pricing are computed per zone. */
@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
public class Zone {

    @Id
    private String id;

    private String name;
    private double latitude;
    private double longitude;

    @Column(name = "map_x")
    private int mapX;

    @Column(name = "map_y")
    private int mapY;
}
