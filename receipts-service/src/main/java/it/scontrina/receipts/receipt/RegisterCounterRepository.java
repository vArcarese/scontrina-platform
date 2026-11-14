package it.scontrina.receipts.receipt;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegisterCounterRepository extends JpaRepository<RegisterCounter, RegisterCounter.Key> {

    @Modifying
    @Query(value = "INSERT INTO register_counter(organization_id, register_id, last_number) " +
            "VALUES (:organizationId, :registerId, 0) " +
            "ON DUPLICATE KEY UPDATE last_number = last_number", nativeQuery = true)
    void insertIfAbsent(@Param("organizationId") String organizationId, @Param("registerId") String registerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RegisterCounter c where c.id = :id")
    RegisterCounter findForUpdate(@Param("id") RegisterCounter.Key id);
}
