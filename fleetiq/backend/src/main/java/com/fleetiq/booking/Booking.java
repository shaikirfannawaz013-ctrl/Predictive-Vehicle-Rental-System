package com.fleetiq.booking;

import com.fleetiq.fleet.Vehicle;
import com.fleetiq.user.User;
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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "actual_return_time")
    private Instant actualReturnTime;

    private int days;

    @Column(name = "base_rate")
    private BigDecimal baseRate;

    private BigDecimal multiplier;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private BigDecimal deposit;

    @Column(name = "late_fee")
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "late_notified")
    private boolean lateNotified;

    @Column(name = "returned_late")
    private boolean returnedLate;

    @Column(name = "damage_reported")
    private boolean damageReported;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public boolean isOwnedBy(Long userId) {
        return customer.getId().equals(userId);
    }
}
