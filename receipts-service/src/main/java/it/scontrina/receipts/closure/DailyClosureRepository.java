package it.scontrina.receipts.closure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyClosureRepository extends JpaRepository<DailyClosure, Long> {

    Optional<DailyClosure> findByOrganizationIdAndRegisterIdAndBusinessDate(
            String organizationId, String registerId, LocalDate businessDate);

    List<DailyClosure> findByOrganizationIdOrderByBusinessDateDesc(String organizationId);

    @Query("""
        select c.id from DailyClosure c
        where (c.status = it.scontrina.receipts.closure.ClosureStatus.TO_TRANSMIT and c.nextAttemptAt <= :now)
           or (c.status = it.scontrina.receipts.closure.ClosureStatus.IN_PROGRESS and c.leaseUntil < :now)
        order by c.nextAttemptAt
        """)
    List<Long> findCandidateIds(@Param("now") Instant now, Pageable page);

    @Modifying
    @Query("""
        update DailyClosure c
        set c.status = it.scontrina.receipts.closure.ClosureStatus.IN_PROGRESS,
            c.leaseUntil = :leaseUntil,
            c.attempts = c.attempts + 1,
            c.updatedAt = :now
        where c.id = :id
          and ((c.status = it.scontrina.receipts.closure.ClosureStatus.TO_TRANSMIT and c.nextAttemptAt <= :now)
            or (c.status = it.scontrina.receipts.closure.ClosureStatus.IN_PROGRESS and c.leaseUntil < :now))
        """)
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
