package com.fleetiq.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            select p from Payment p join fetch p.booking b join fetch b.vehicle v join fetch v.zone
            join fetch b.customer where p.reference = :reference
            """)
    Optional<Payment> findByReferenceDetailed(@Param("reference") String reference);

    @Modifying
    @Query("""
            update Payment p set p.status = com.fleetiq.payment.PaymentState.REFUNDED
            where p.booking.id = :bookingId and p.status = com.fleetiq.payment.PaymentState.SUCCESS
            """)
    int markRefunded(@Param("bookingId") Long bookingId);
}
