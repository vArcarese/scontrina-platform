package it.scontrina.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    // TODO: query per prelevare un blocco di eventi non pubblicati, con lock a livello di riga
}
