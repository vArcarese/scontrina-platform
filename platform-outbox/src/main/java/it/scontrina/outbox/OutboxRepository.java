package it.scontrina.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    @Query(value = "SELECT * FROM outbox_event " +
            "WHERE published_at IS NULL " +
            "ORDER BY created_at LIMIT :limit " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findPendingByLimit(@Param("limit") int limit);

}
