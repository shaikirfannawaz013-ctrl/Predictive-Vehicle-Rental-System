package com.fleetiq.allocation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AllocationMoveRepository extends JpaRepository<AllocationMove, Long> {

    @Modifying
    @Query("delete from AllocationMove m where m.status = :status")
    int deleteByStatus(@Param("status") String status);

    @Query("""
            select m from AllocationMove m join fetch m.vehicle join fetch m.fromZone join fetch m.toZone
            where m.id = :id
            """)
    Optional<AllocationMove> findDetailed(@Param("id") Long id);
}
