package com.fleetiq.fleet;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Query("select v from Vehicle v join fetch v.zone order by v.id")
    List<Vehicle> findAllWithZone();

    @Query("select v from Vehicle v join fetch v.zone where v.id = :id")
    Optional<Vehicle> findWithZone(@Param("id") Long id);

    /** Row lock so two customers can't book the same vehicle for overlapping dates at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);

    @Query("select v.zone.id, count(v) from Vehicle v where v.status = :status group by v.zone.id")
    List<Object[]> countByZoneAndStatus(@Param("status") VehicleStatus status);

    long countByStatus(VehicleStatus status);

    @Modifying
    @Query("update Vehicle v set v.healthScore = :score where v.id = :id")
    int updateHealthScore(@Param("id") Long id, @Param("score") int score);
}
