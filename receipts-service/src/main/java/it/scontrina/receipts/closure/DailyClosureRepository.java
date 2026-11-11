package it.scontrina.receipts.closure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyClosureRepository extends JpaRepository<DailyClosure, Long> {

    Optional<DailyClosure> findByOrganizationIdAndRegisterIdAndBusinessDate(
            String organizationId, String registerId, LocalDate businessDate);

    List<DailyClosure> findByOrganizationIdOrderByBusinessDateDesc(String organizationId);

    // TODO: query delle chiusure candidate e presa in carico atomica (UPDATE condizionale)
}
