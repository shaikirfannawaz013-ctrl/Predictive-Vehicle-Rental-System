package com.fleetiq.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    String DETAILED = "select b from Booking b join fetch b.vehicle v join fetch v.zone join fetch b.customer ";

    /** Vehicles that already have a booking (or a live payment hold) overlapping [start, end). */
    @Query("""
            select distinct b.vehicle.id from Booking b
            where b.startTime < :end and b.endTime > :start
              and (b.status in :occupying or (b.status = :pending and b.holdExpiresAt > :now))
            """)
    List<Long> findBlockedVehicleIds(@Param("start") Instant start, @Param("end") Instant end,
                                     @Param("occupying") Collection<BookingStatus> occupying,
                                     @Param("pending") BookingStatus pending, @Param("now") Instant now);

    @Query("""
            select count(b) from Booking b
            where b.vehicle.id = :vehicleId and b.startTime < :end and b.endTime > :start
              and (b.status in :occupying or (b.status = :pending and b.holdExpiresAt > :now))
            """)
    long countOverlaps(@Param("vehicleId") Long vehicleId, @Param("start") Instant start, @Param("end") Instant end,
                       @Param("occupying") Collection<BookingStatus> occupying,
                       @Param("pending") BookingStatus pending, @Param("now") Instant now);

    @Query(DETAILED + "where b.id = :id")
    Optional<Booking> findDetailed(@Param("id") Long id);

    @Query(DETAILED + "where b.customer.id = :customerId order by b.createdAt desc")
    List<Booking> findForCustomer(@Param("customerId") Long customerId);

    @Query(DETAILED + "where b.status = :status and b.endTime < :before order by b.endTime")
    List<Booking> findByStatusEndingBefore(@Param("status") BookingStatus status, @Param("before") Instant before);

    @Query(DETAILED + "where b.status = :status and b.startTime <= :now")
    List<Booking> findByStatusStartedBy(@Param("status") BookingStatus status, @Param("now") Instant now);

    @Query(DETAILED + "where b.status = :status and b.holdExpiresAt < :now")
    List<Booking> findExpiredHolds(@Param("status") BookingStatus status, @Param("now") Instant now);

    @Query("""
            select v.zone.id, count(b) from Booking b join b.vehicle v
            where b.status in :statuses and b.startTime >= :from and b.startTime < :to
            group by v.zone.id
            """)
    List<Object[]> countStartingByZone(@Param("statuses") Collection<BookingStatus> statuses,
                                       @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select b.vehicle.id, count(b) from Booking b
            where b.status in :statuses and b.startTime >= :since
            group by b.vehicle.id
            """)
    List<Object[]> countTripsPerVehicleSince(@Param("statuses") Collection<BookingStatus> statuses,
                                             @Param("since") Instant since);

    @Query("""
            select v.type, count(b) from Booking b join b.vehicle v
            where b.status in :statuses and b.startTime >= :since
            group by v.type
            """)
    List<Object[]> countPerTypeSince(@Param("statuses") Collection<BookingStatus> statuses,
                                     @Param("since") Instant since);
}
