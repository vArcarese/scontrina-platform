package it.scontrina.receipts.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, String> {

    Optional<Receipt> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsByVoidedReceiptId(String voidedReceiptId);

    List<Receipt> findByOrganizationIdAndRegisterIdAndBusinessDateOrderByNumber(
            String organizationId, String registerId, LocalDate businessDate);
}
